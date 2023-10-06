package com.queryeer;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.IConfigurable.EncryptionResult;
import com.queryeer.api.service.ICryptoService;

/** Test of crypto service */
public class CryptoServiceTest
{
    private ServiceLoader serviceLoader = Mockito.mock(ServiceLoader.class);
    @SuppressWarnings("unchecked")
    private MutableObject<char[]> password = Mockito.mock(MutableObject.class);
    private CryptoService cryptoService = new CryptoService(serviceLoader)
    {
        @Override
        protected char[] getMasterPassword(String passwordLabel, String title, String message)
        {
            return password.getValue();
        };

        @Override
        protected void showErrorMessage(javax.swing.JComponent parent, String message)
        {
        };
    };

    @Test
    public void test_decrypt()
    {
        assertNull(cryptoService.decryptString(null));
        assertEquals("non-encrypted-string", cryptoService.decryptString("non-encrypted-string"));
        assertEquals(CryptoService.PREFIX + "non-encrypted-string", cryptoService.decryptString(CryptoService.PREFIX + "non-encrypted-string"));
        assertEquals("non-encrypted-string" + CryptoService.SUFFIX, cryptoService.decryptString("non-encrypted-string" + CryptoService.SUFFIX));

        // Null master password => null decrypt string
        assertNull(cryptoService.decryptString(CryptoService.PREFIX + "somevalue" + CryptoService.SUFFIX));

        when(password.getValue()).thenReturn("password".toCharArray());

        String encrypted = cryptoService.encryptString("value");

        assertEquals("value", cryptoService.decryptString(encrypted));
    }

    @Test
    public void test_encrypt()
    {
        assertNull(cryptoService.encryptString(null));

        // Encrypt encrypted string does nothing
        assertEquals(CryptoService.PREFIX + "somevalue" + CryptoService.SUFFIX, cryptoService.encryptString(CryptoService.PREFIX + "somevalue" + CryptoService.SUFFIX));

        // Null master password => null encrypt string
        assertNull(cryptoService.encryptString("non-encrypted-string"));

        when(password.getValue()).thenReturn("password".toCharArray());

        String actual = cryptoService.encryptString("value");

        // Cannot assert the crypto string, obviously
        assertTrue(actual.startsWith(CryptoService.PREFIX));
        assertTrue(actual.endsWith(CryptoService.SUFFIX));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void test_changeMasterPassword_success()
    {
        // Set up service
        when(password.getValue()).thenReturn("dummy".toCharArray());
        cryptoService.encryptString("dummy");

        Mockito.reset(password);

        // New master password
        when(password.getValue()).thenReturn("new-master".toCharArray());

        IConfigurable config1 = mock(IConfigurable.class);

        String expected = "value";
        MutableObject<String> expectedEncrypted = new MutableObject<>();

        when(config1.reEncryptSecrets(Mockito.any(ICryptoService.class))).then(new Answer<EncryptionResult>()
        {
            @Override
            public IConfigurable.EncryptionResult answer(InvocationOnMock invocation) throws Throwable
            {
                ICryptoService cs = (ICryptoService) invocation.getArgument(0);
                expectedEncrypted.setValue(cs.encryptString(expected));
                return IConfigurable.EncryptionResult.SUCCESS;
            }
        });

        IConfigurable config2 = mock(IConfigurable.class);
        when(config2.reEncryptSecrets(Mockito.any(ICryptoService.class))).thenReturn(IConfigurable.EncryptionResult.NO_CHANGE);

        cryptoService.changeMasterPassword(asList(config1, config2));

        // Verify that we can decrypt with new master password
        assertEquals(expected, cryptoService.decryptString(expectedEncrypted.getValue()));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void test_that_master_password_is_changed_even_if_no_configurables_changed()
    {
        // Set up service
        when(password.getValue()).thenReturn("dummy".toCharArray());
        cryptoService.encryptString("dummy");

        String oldEncryptedValue = cryptoService.encryptString("message");

        Mockito.reset(password);

        // New master password
        when(password.getValue()).thenReturn("new-master".toCharArray());

        IConfigurable config1 = mock(IConfigurable.class);
        when(config1.reEncryptSecrets(Mockito.any(ICryptoService.class))).thenReturn(IConfigurable.EncryptionResult.SUCCESS);

        IConfigurable config2 = mock(IConfigurable.class);
        when(config2.reEncryptSecrets(Mockito.any(ICryptoService.class))).thenReturn(IConfigurable.EncryptionResult.NO_CHANGE);

        cryptoService.changeMasterPassword(asList(config1, config2));

        Mockito.verify(config1, times(1))
                .reEncryptSecrets(any(ICryptoService.class));
        Mockito.verify(config2, times(1))
                .reEncryptSecrets(any(ICryptoService.class));
        Mockito.verify(config1, times(1))
                .commitChanges();

        String newEncryptedValue = cryptoService.encryptString("message");
        assertEquals("message", cryptoService.decryptString(newEncryptedValue));
        // Verify that we cannot decrypt the old value since we switched master
        assertNull(cryptoService.decryptString(oldEncryptedValue));

        Mockito.verifyNoMoreInteractions(config1, config2);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void test_that_master_password_is_NOT_changed_if_one_configurable_aborts()
    {
        // Set up service
        when(password.getValue()).thenReturn("dummy".toCharArray());
        cryptoService.encryptString("dummy");

        String oldEncryptedValue = cryptoService.encryptString("message");

        Mockito.reset(password);

        // New master password
        when(password.getValue()).thenReturn("new-master".toCharArray());

        IConfigurable config1 = mock(IConfigurable.class);
        when(config1.reEncryptSecrets(Mockito.any(ICryptoService.class))).thenReturn(IConfigurable.EncryptionResult.SUCCESS);

        IConfigurable config2 = mock(IConfigurable.class);
        when(config2.reEncryptSecrets(Mockito.any(ICryptoService.class))).thenReturn(IConfigurable.EncryptionResult.ABORT);

        cryptoService.changeMasterPassword(asList(config1, config2));

        Mockito.verify(config1, times(1))
                .reEncryptSecrets(any(ICryptoService.class));
        Mockito.verify(config2, times(1))
                .reEncryptSecrets(any(ICryptoService.class));
        Mockito.verify(config1, times(1))
                .revertChanges();

        String newEncryptedValue = cryptoService.encryptString("message");
        assertEquals("message", cryptoService.decryptString(newEncryptedValue));
        assertEquals("message", cryptoService.decryptString(oldEncryptedValue));

        Mockito.verifyNoMoreInteractions(config1, config2);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void test_that_master_password_is_NOT_changed_if_one_configurable_throws()
    {
        // Set up service
        when(password.getValue()).thenReturn("dummy".toCharArray());
        cryptoService.encryptString("dummy");

        String oldEncryptedValue = cryptoService.encryptString("message");

        Mockito.reset(password);

        // New master password
        when(password.getValue()).thenReturn("new-master".toCharArray());

        IConfigurable config1 = mock(IConfigurable.class);
        when(config1.reEncryptSecrets(Mockito.any(ICryptoService.class))).thenReturn(IConfigurable.EncryptionResult.SUCCESS);

        IConfigurable config2 = mock(IConfigurable.class);
        when(config2.reEncryptSecrets(Mockito.any(ICryptoService.class))).thenThrow(new RuntimeException());

        cryptoService.changeMasterPassword(asList(config1, config2));

        Mockito.verify(config1, times(1))
                .reEncryptSecrets(any(ICryptoService.class));
        Mockito.verify(config2, times(1))
                .reEncryptSecrets(any(ICryptoService.class));
        Mockito.verify(config1, times(1))
                .revertChanges();

        String newEncryptedValue = cryptoService.encryptString("message");
        assertEquals("message", cryptoService.decryptString(newEncryptedValue));
        assertEquals("message", cryptoService.decryptString(oldEncryptedValue));

        Mockito.verifyNoMoreInteractions(config1, config2);
    }

}
