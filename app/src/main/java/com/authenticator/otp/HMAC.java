package com.authenticator.otp;
import javax.crypto.Mac;
import javax.crypto.MacSpi;
import java.security.Provider;

public class HMAC extends Mac {
    String algorithm;

    protected HMAC(MacSpi macSpi, Provider provider, String algorithm) {
        super(macSpi, provider, algorithm);

    }
}
