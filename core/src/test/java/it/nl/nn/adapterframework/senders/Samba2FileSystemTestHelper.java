package it.nl.nn.adapterframework.senders;

import static com.hierynomus.msfscc.FileAttributes.FILE_ATTRIBUTE_DIRECTORY;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.login.LoginContext;

import org.apache.commons.lang.StringUtils;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;
import org.junit.After;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mserref.NtStatus;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.protocol.commons.EnumWithValue;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.auth.GSSAuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.Directory;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;

import nl.nn.adapterframework.configuration.ConfigurationException;
import nl.nn.adapterframework.filesystem.FileSystemException;
import nl.nn.adapterframework.filesystem.IFileSystemTestHelper;
import nl.nn.adapterframework.filesystem.KerberosLoginConfiguration;
import nl.nn.adapterframework.filesystem.UsernameAndPasswordCallbackHandler;

public class Samba2FileSystemTestHelper implements IFileSystemTestHelper {

	private String authType = "NTLM";
	private String shareName = "Shared";
	private String username = "";
	private String password = "";
	private String domain = "";
	private String realm = "";
	private String kdc = "";

	private DiskShare client = null;
	private Session session = null;
	private Connection connection = null;
	private SMBClient smbClient = null;
		
	public Samba2FileSystemTestHelper(String shareName, String username, String password, String domain, String realm, String kdc, String authType) {
		this.shareName=shareName;
		this.username=username;
		this.password=password;
		this.domain=domain;
		this.realm=realm;
		this.kdc=kdc;
		this.authType = authType;
	}
	
	@Override
	public void setUp() throws IOException, ConfigurationException, FileSystemException {
		AuthenticationContext auth = authenticate();
		open(auth);
		
		List<FileIdBothDirectoryInformation> list = client.list("");
        for (FileIdBothDirectoryInformation fi : list) {
            if (fi.getFileName().equals(".") || fi.getFileName().equals("..")) {
                continue;
            }
            String childPath = fi.getFileName();
            if (!EnumWithValue.EnumUtils.isSet(fi.getFileAttributes(), FILE_ATTRIBUTE_DIRECTORY)) {
                client.rm(childPath);
            } else {
                client.rmdir(childPath, true);
            }
        }
	}
	
	@Override
	@After
	public void tearDown() throws Exception {
		if (client != null) {
			client.close();
		}
		if (session != null) {
			session.close();
		}
		if (connection != null) {
			connection.close();
		}
	}

	private AuthenticationContext authenticate() throws FileSystemException {
		if(StringUtils.equalsIgnoreCase(authType, "NTLM")) {
			return new AuthenticationContext(username, password.toCharArray(), domain);
		}else if(StringUtils.equalsIgnoreCase(authType, "SPNEGO")) {
			if(!StringUtils.isEmpty(kdc) && !StringUtils.isEmpty(realm)) {
				System.setProperty("java.security.krb5.kdc", kdc);
				System.setProperty("java.security.krb5.realm", realm);
			}
			HashMap<String, String> loginParams = new HashMap<String, String>();
			loginParams.put("principal", username);
			LoginContext lc;
			try {
				lc = new LoginContext(username, null, 
						new UsernameAndPasswordCallbackHandler(username, password),
						new KerberosLoginConfiguration(loginParams));
				lc.login();
				
				Subject subject = lc.getSubject();
				KerberosPrincipal krbPrincipal = subject.getPrincipals(KerberosPrincipal.class).iterator().next();

				Oid spnego = new Oid("1.3.6.1.5.5.2");
				Oid kerberos5 = new Oid("1.2.840.113554.1.2.2");

				final GSSManager manager = GSSManager.getInstance();

				final GSSName name = manager.createName(krbPrincipal.toString(), GSSName.NT_USER_NAME);
				Set<Oid> mechs = new HashSet<Oid>(Arrays.asList(manager.getMechsForName(name.getStringNameType())));
				final Oid mech;

				if (mechs.contains(kerberos5)) {
					mech = kerberos5;
				} else if (mechs.contains(spnego)) {
					mech = spnego;
				} else {
					throw new IllegalArgumentException("No mechanism found");
				}

				GSSCredential creds = Subject.doAs(subject, new PrivilegedExceptionAction<GSSCredential>() {
					@Override
					public GSSCredential run() throws GSSException {
						return manager.createCredential(name, GSSCredential.DEFAULT_LIFETIME, mech, GSSCredential.INITIATE_ONLY);
					}
				});

				GSSAuthenticationContext auth = new GSSAuthenticationContext(krbPrincipal.getName(), krbPrincipal.getRealm(), subject, creds);
				return auth;

			} catch (Exception e) {
				throw new FileSystemException(e);
			}
		}
		return null;
	}

	public void open(AuthenticationContext auth) throws FileSystemException {
		if (smbClient == null) {
			smbClient = new SMBClient();
		}
		try {
			if (connection == null) {
				connection = smbClient.connect(domain);
			}
			if (session == null) {
				session = connection.authenticate(auth);
			}
			if (client == null) {
				client = (DiskShare) session.connectShare(shareName);
			}
		} catch (IOException e) {
			throw new FileSystemException("Cannot connect to samba server", e);
		}
	}
	@Override
	public boolean _fileExists(String folder, String filename) throws Exception {
		String path = folder != null ? folder + "\\" + filename : filename;
		try {
			return client.fileExists(path);
		} catch (SMBApiException e) {
			if (e.getStatus().equals(NtStatus.STATUS_DELETE_PENDING))
				return false;
			throw e;
		}
	}

	@Override
	public void _deleteFile(String folder, String filename) throws Exception {
		String path = folder != null ? folder + "\\" + filename : filename;
		client.rm(path);

	}

	@Override
	public OutputStream _createFile(String folder, String filename) throws Exception {
		Set<SMB2CreateOptions> createOptions = new HashSet<SMB2CreateOptions>();
		createOptions.add(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE);
		Set<AccessMask> accessMask = new HashSet<AccessMask>(EnumSet.of(AccessMask.GENERIC_ALL));
		String path = folder != null ? folder + "\\" + filename : filename;
		final File file = client.openFile(path, accessMask, null, SMB2ShareAccess.ALL,
				SMB2CreateDisposition.FILE_OVERWRITE_IF, createOptions);

		return file.getOutputStream();
	}

	@Override
	public InputStream _readFile(String folder, String filename) throws Exception {
		Set<AccessMask> accessMask = new HashSet<AccessMask>();
		accessMask.add(AccessMask.GENERIC_READ);
		Set<SMB2ShareAccess> shareAccess = new HashSet<SMB2ShareAccess>();
		shareAccess.addAll(SMB2ShareAccess.ALL);
		final File file;
		String path = folder != null ? folder + "\\" + filename : filename;
		file = client.openFile(path, accessMask, null, shareAccess, SMB2CreateDisposition.FILE_OPEN, null);

		return file.getInputStream();
	}

	@Override
	public void _createFolder(String filename) throws Exception {
		Set<AccessMask> accessMask = new HashSet<AccessMask>();
		accessMask.add(AccessMask.FILE_ADD_FILE);
		Set<SMB2ShareAccess> shareAccess = new HashSet<SMB2ShareAccess>();
		shareAccess.addAll(SMB2ShareAccess.ALL);
		Directory dir = client.openDirectory(filename, accessMask, null, shareAccess,
				SMB2CreateDisposition.FILE_OPEN_IF, null);
		dir.close();
	}

	@Override
	public boolean _folderExists(String folderName) throws Exception {
		try {
			return client.folderExists(folderName);
		} catch (SMBApiException e) {
			if (e.getStatus().equals(NtStatus.STATUS_DELETE_PENDING))
				return false;
			throw e;
		}
	}

	@Override
	public void _deleteFolder(String folderName) throws Exception {
		client.rmdir(folderName, true);
	}
}