package jenkins.plugins.openstack;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

class RsaUtils {
	private static final String ALGORITHM = "RSA";
	// private static final String PROVIDER = "SunJSSE";
	private static final int DEFAULT_KEYSIZE = 2048;

	public static KeyPair generateRsaKeyPair() {
		return generateRsaKeyPair(DEFAULT_KEYSIZE);
	}

	public static KeyPair generateRsaKeyPair(int keysize) {
		return generateRsaKeyPair(ALGORITHM, keysize);
	}

	public static KeyPair generateRsaKeyPair(String algorithm, int keysize) {
		KeyPairGenerator generator;
		try {
			generator = KeyPairGenerator.getInstance(algorithm);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("Error loading crypto provider", e);
		}
		generator.initialize(keysize);
		KeyPair keyPair = generator.generateKeyPair();
		return keyPair;

	}

}
