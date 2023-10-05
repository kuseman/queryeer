package com.queryeer.api.service;

/**
 * Definition of encryption service. Can be used by implementations to encrypt/decrypt properties in config files etc.
 */
public interface ICryptoService
{
    /**
     * Decrypt provided string value. NOTE! If service is unlocked a dialog will appear that asks for master password.
     *
     * @return Returns the decrypted value or null if decryption was aborted or failed.
     */
    public String decryptString(String value);

    /**
     * Encrypt provided string value NOTE! If service is unlocked a dialog will appear that asks for master password.
     *
     * @return Returns the encrypted value or null if encryption was aborted or failed.
     */
    public String encryptString(String value);

    /** Returns true if service initalized with a master password */
    public boolean isInitalized();
}
