package jenkins.plugins.openstack;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.DigestInputStream;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.interfaces.RSAPublicKey;

import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.openssl.PasswordFinder;
import org.bouncycastle.util.encoders.Base64;
import org.openstack.utils.Utf8;

public class OpenSshUtils {
    private static final String SSH_RSA_PREFIX = "ssh-rsa ";
    private static final String KEYTYPE_RSA = "ssh-rsa";

    // TODO: I can't believe this isn't in Bouncycastle or something!!

    // static class KeyInputStream implements Closeable {
    // final InputStream is;
    //
    // static final int MAX_BUFFER_SIZE = 32768;
    //
    // public KeyInputStream(InputStream is) {
    // super();
    // this.is = is;
    // }
    //
    // public void close() throws IOException {
    // is.close();
    // }
    //
    // int readUint8() throws IOException {
    // int v = is.read();
    // if (v == -1)
    // throw new IOException("EOF");
    // return v;
    // }
    //
    // long readUint32() throws IOException {
    // long value = readUint8();
    // value <<= 8;
    // value |= readUint8();
    // value <<= 8;
    // value |= readUint8();
    // value <<= 8;
    // value |= readUint8();
    // return value;
    // }
    //
    // public byte[] readByteArray() throws IOException {
    // long length = readUint32();
    // if (length > MAX_BUFFER_SIZE)
    // throw new IllegalStateException();
    // byte[] buffer = new byte[(int) length];
    // Io.readFully(is, buffer, 0, (int) length);
    // return buffer;
    // }
    //
    // public String readString() throws IOException {
    // return Utf8.toString(readByteArray());
    // }
    //
    // public BigInteger readBigInteger() throws IOException {
    // byte[] data = readByteArray();
    // if (data.length == 0) {
    // return BigInteger.ZERO;
    // }
    //
    // return new BigInteger(data);
    // }
    // }
    //
    // public static PublicKey readSshPublicKey(String sshPublicKey) throws IOException {
    // if (Strings.isNullOrEmpty(sshPublicKey))
    // return null;
    // // StringReader reader = new StringReader(sshPublicKey);
    // // PEMReader pemReader = new PEMReader(reader);
    // // return (PublicKey) pemReader.readObject();
    //
    // if (sshPublicKey.startsWith(SSH_RSA_PREFIX)) {
    // String base64 = sshPublicKey.substring(SSH_RSA_PREFIX.length());
    // byte[] data = Io.fromBase64(base64);
    //
    // KeyInputStream is = new KeyInputStream(new ByteArrayInputStream(data));
    // try {
    // String keyType = is.readString();
    //
    // if (keyType.equals(KEYTYPE_RSA)) {
    // final BigInteger publicExponent = is.readBigInteger();
    // final BigInteger modulus = is.readBigInteger();
    //
    // final RSAPublicKeySpec rsaPubSpec = new RSAPublicKeySpec(modulus, publicExponent);
    //
    // try {
    // KeyFactory rsaKeyFact = KeyFactory.getInstance("RSA");
    // return rsaKeyFact.generatePublic(rsaPubSpec);
    // } catch (NoSuchAlgorithmException e) {
    // throw new IllegalStateException("Error loading RSA provider", e);
    // } catch (InvalidKeySpecException e) {
    // throw new IOException("Key data is corrupted", e);
    // }
    // } else {
    // throw new IOException("Unhandled key type: " + keyType);
    // }
    // } finally {
    // Io.safeClose(is);
    // }
    // } else {
    // throw new IOException("Unknown key format: " + sshPublicKey);
    // }
    // }

    static class KeyOutputStream implements Closeable {
        final OutputStream os;

        static final int MAX_BUFFER_SIZE = 32768;

        public KeyOutputStream(OutputStream os) {
            super();
            this.os = os;
        }

        public void close() throws IOException {
            os.close();
        }

        void writeUint32(long value) throws IOException {
            byte[] tmp = new byte[4];
            tmp[0] = (byte) ((value >>> 24) & 0xff);
            tmp[1] = (byte) ((value >>> 16) & 0xff);
            tmp[2] = (byte) ((value >>> 8) & 0xff);
            tmp[3] = (byte) (value & 0xff);
            os.write(tmp);
        }

        public void writeByteArray(byte[] data) throws IOException {
            writeUint32(data.length);
            os.write(data);
        }

        public void writeString(String data) throws IOException {
            writeByteArray(Utf8.getBytes(data));
        }

        public void writeBigInteger(BigInteger value) throws IOException {
            if (value.equals(BigInteger.ZERO)) {
                writeUint32(0);
            } else {
                writeByteArray(value.toByteArray());
            }
        }
    }

    static byte[] encodePublicKey(RSAPublicKey key) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        KeyOutputStream out = new KeyOutputStream(baos);
        try {
            out.writeString(KEYTYPE_RSA);
            out.writeBigInteger(key.getPublicExponent());
            out.writeBigInteger(key.getModulus());
        } finally {
            out.close();
        }
        return baos.toByteArray();
    }

    public static String serialize(PublicKey sshPublicKey) throws IOException {
        if (sshPublicKey == null)
            return null;
        return SSH_RSA_PREFIX + toBase64(encodePublicKey((RSAPublicKey) sshPublicKey));
    }

    public static String toBase64(byte[] data) {
        return new String(Base64.encode(data));
    }

    public static String serialize(PrivateKey sshPrivateKey) throws IOException {
        if (sshPrivateKey == null)
            return null;
        StringWriter stringWriter = new StringWriter();
        PEMWriter writer = new PEMWriter(stringWriter);
        writer.writeObject(sshPrivateKey);
        writer.close();

        return stringWriter.toString();
    }

    static {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    private static final RuntimeException PRIVATE_KEY_WITH_PASSWORD = new RuntimeException();

    /**
     * Obtains the fingerprint of the key in the "ab:cd:ef:...:12" format.
     */
    public static String getFingerprint(String privateKey) throws IOException {
        Reader r = new BufferedReader(new StringReader(privateKey));
        PEMReader pem = new PEMReader(r, new PasswordFinder() {
            public char[] getPassword() {
                throw PRIVATE_KEY_WITH_PASSWORD;
            }
        });

        try {
            KeyPair pair = (KeyPair) pem.readObject();
            if (pair == null)
                return null;
            PrivateKey key = pair.getPrivate();
            return digest(key);
        } catch (RuntimeException e) {
            if (e == PRIVATE_KEY_WITH_PASSWORD)
                throw new IOException("This private key is password protected, which isn't supported yet");
            throw e;
        }
    }

    /* package */static String digest(PrivateKey k) throws IOException {
        try {
            MessageDigest md5 = MessageDigest.getInstance("SHA1");

            DigestInputStream in = new DigestInputStream(new ByteArrayInputStream(k.getEncoded()), md5);
            try {
                while (in.read(new byte[128]) > 0)
                    ; // simply discard the input
            } finally {
                in.close();
            }
            StringBuilder buf = new StringBuilder();
            char[] hex = Hex.encodeHex(md5.digest());
            for (int i = 0; i < hex.length; i += 2) {
                if (buf.length() > 0)
                    buf.append(':');
                buf.append(hex, i, 2);
            }
            return buf.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
