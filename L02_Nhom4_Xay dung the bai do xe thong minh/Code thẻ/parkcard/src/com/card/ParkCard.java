package com.card;

import javacard.framework.Util;
import javacard.framework.Applet;
import javacard.framework.APDU;
import javacard.framework.JCSystem;
import javacard.framework.ISOException;
import javacard.framework.ISO7816;
import javacard.security.AESKey;
import javacard.security.KeyBuilder;
import javacardx.apdu.ExtendedLength;
import javacardx.crypto.Cipher;
import javacard.security.KeyPair;
import javacard.security.RSAPrivateKey;
import javacard.security.RSAPublicKey;
import javacard.security.Signature;
import javacard.security.RandomData;

public class ParkCard extends Applet implements ExtendedLength {
    public static final byte NUMBER_RETRY = 5;
    public static final byte ROLE_ADMIN = (byte) 0x01;
    public static final short SW_PIN_SAME_AS_OLD = (short) 0x6A80;
    
    // Khoa 16 bytes dung de ma hoa Pin tren duong truyen (Transport Key)
    // "KeyBaoMatPin2026"
    private static final byte[] TRANSPORT_KEY_DATA = { 
        (byte)0x4B, (byte)0x65, (byte)0x79, (byte)0x42, (byte)0x61, (byte)0x6F, (byte)0x4D, (byte)0x61,
        (byte)0x74, (byte)0x50, (byte)0x69, (byte)0x6E, (byte)0x32, (byte)0x30, (byte)0x32, (byte)0x36 
    };
    
    private PIN systemSecret; 
    private boolean isSecretValidated;
    
    private PBKDF2 pbkdf2Engine;
    
    static final byte INS_VERIFY_PIN = (byte) 0x00;
    static final byte INS_CREATE_PIN = (byte) 0x01;
    static final byte INS_CREATE_INFO = (byte) 0x02;
    static final byte INS_UPDATE_PIN = (byte) 0x03;
    static final byte INS_UPDATE_INFO = (byte) 0x04;
    static final byte INS_GET_INFO = (byte) 0x05;
    static final byte INS_UPDATE_MONEY = (byte) 0x06;
    static final byte INS_GET_MONEY = (byte) 0x07;
    static final byte INS_UPDATE_EXPIRED = (byte) 0x08;
    static final byte INS_RESET_PIN = (byte) 0x09;
    static final byte INS_VERIFY_SECRET = (byte) 0x0A;
    
    static final byte INS_UPDATE_AVATAR = (byte) 0x10;
    static final byte INS_GET_AVATAR = (byte) 0x11;
    static final byte INS_CREATE_SIGNATURE = (byte) 0x12;
    //static final byte INS_GET_PUBLIC_KEY = (byte) 0x13;
    static final byte INS_SET_BACKEND_PUBLIC_KEY = (byte) 0x16;
    static final byte INS_GET_CARD_ID = (byte) 0x17;
    static final byte INS_GET_DB_ID = (byte) 0x18;
    
    static final byte INS_ENTRY_GATE = (byte) 0x20;
    static final byte INS_EXIT_GATE = (byte) 0x21;
    static final byte INS_GET_ENTRY_STATE = (byte) 0x22;
    static final byte INS_GET_INFO_NO = (byte) 0x23;
    static final byte INS_GET_PIN_TRIES = (byte) 0x24;
    static final byte INS_UPDATE_PIN_DIRECT = (byte) 0x25;
    static final byte INS_UPDATE_MONEY_DIRECT = (byte) 0x26;
    static final byte INS_CHECK_EMPTY = (byte) 0x27;
    static final byte INS_CHECK_VALIDITY = (byte) 0x30;

    public static final short MAX_SIZE_AVATAR = (short) 8192;
    public static final short ENCRYPTED_INFO_SIZE = (short) 48; 
    
    private PIN pin;
    private CardModel cardModel;

    private AESKey masterKeyObj;   
    private AESKey wrappingKeyObj; 
    private AESKey transportKeyObj; // Key AES dung de giai ma Pin gui xuong
    private RandomData rng;

    private byte[] masterKeyRAM;              
    private byte[] encryptedMasterKey_User;  
    private byte[] encryptedMasterKey_Admin; 
    
   
    private byte[] encryptedPrivateKeyExp;

    private final Cipher cipherData; 
    private final Cipher cipherWrap; 

    private final byte[] temporary;    
    private final byte[] cipherBuffer; 
    private final byte[] iv;
    
    private RSAPublicKey cachedBackendPubKey = null;
    private Signature cachedSigVerify = null;
    private Signature signature;
    private final byte[] signatureBuffer;
    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private final byte[] tempMessage;
    private final byte[] tempSignature;
    private final byte[] tempPinData;

    public ParkCard() {
        pbkdf2Engine = new PBKDF2();
        
        rng = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);
        masterKeyObj = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, (short) 128, false);
        wrappingKeyObj = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, (short) 128, false);
        
        // Khoi tao key transport
        transportKeyObj = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, (short) 128, false);
        transportKeyObj.setKey(TRANSPORT_KEY_DATA, (short) 0);

        cipherData = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD, false);
        cipherWrap = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_ECB_NOPAD, false);

        masterKeyRAM = JCSystem.makeTransientByteArray((short) 16, JCSystem.CLEAR_ON_RESET);
        encryptedMasterKey_User = new byte[16];
        encryptedMasterKey_Admin = new byte[16];
        // Khoi tao mang luu Private Key ma hoa
        encryptedPrivateKeyExp = new byte[128];

        temporary = JCSystem.makeTransientByteArray((short) 64, JCSystem.CLEAR_ON_RESET);
        cipherBuffer = JCSystem.makeTransientByteArray((short) 64, JCSystem.CLEAR_ON_RESET);
        
        iv = new byte[16];
        Util.arrayFillNonAtomic(iv, (short)0, (short)16, (byte)0x00);

        signature = Signature.getInstance(Signature.ALG_RSA_SHA_PKCS1, false);
        signatureBuffer = JCSystem.makeTransientByteArray((short) (KeyBuilder.LENGTH_RSA_1024 / 8), JCSystem.CLEAR_ON_RESET);
        tempMessage = JCSystem.makeTransientByteArray((short) 256, JCSystem.CLEAR_ON_RESET);
        tempSignature = JCSystem.makeTransientByteArray((short) 128, JCSystem.CLEAR_ON_RESET);
        tempPinData = JCSystem.makeTransientByteArray((short) 32, JCSystem.CLEAR_ON_RESET);
        
        isSecretValidated = false;
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new ParkCard().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public void deselect() {
        if (pin != null) pin.logout();
        if (systemSecret != null) systemSecret.reset();
        isSecretValidated = false;
        clearSession();
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            clearSession();
            return;
        }

        byte[] buf = apdu.getBuffer();
        switch (buf[ISO7816.OFFSET_INS]) {
            case INS_VERIFY_SECRET: 
                verifySecret(apdu);
                break;
            case INS_CREATE_PIN: createPIN(apdu); break;
            case INS_VERIFY_PIN: verifyPIN(apdu); break;
            case INS_UPDATE_PIN: updatePIN(apdu); break;
            case INS_RESET_PIN: resetPIN(apdu); break;
            
            case INS_UPDATE_PIN_DIRECT: updatePINDirect(apdu); break;
            
            case INS_CREATE_INFO: createCard(apdu); break;
            
            case INS_GET_INFO: 
                if ( (pin == null || !pin.isValidated()) && !isSecretValidated ) {
                      ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
                }
                getCard(apdu); 
                break;
                
            case INS_UPDATE_INFO: updateInfo(apdu); break;
            case INS_UPDATE_MONEY: updateMoney(apdu); break;
            case INS_GET_MONEY: getMoney(apdu); break;
            case INS_UPDATE_EXPIRED: updateExpired(apdu); break;
            case INS_UPDATE_MONEY_DIRECT: updateMoneyDirect(apdu); break;

            case INS_GET_AVATAR: getAvatar(apdu); break;
            case INS_UPDATE_AVATAR: updateAvatar(apdu); break;

            case INS_CREATE_SIGNATURE: createSignature(apdu); break;
            //case INS_GET_PUBLIC_KEY: getPublicKey(buf, apdu); break;
            case INS_SET_BACKEND_PUBLIC_KEY: setBackendPublicKey(apdu); break;
            case INS_GET_CARD_ID: getCardId(apdu); break;
            case INS_GET_DB_ID: getDbId(apdu); break;
            
            case INS_ENTRY_GATE: entryGate(apdu); break;
            case INS_EXIT_GATE: exitGate(apdu); break;
            case INS_GET_ENTRY_STATE: getEntryState(apdu); break;
            case INS_GET_INFO_NO: getCardNO(apdu); break;
            case INS_GET_PIN_TRIES: getPinTries(apdu); break;
            case INS_CHECK_EMPTY: checkEmpty(apdu); break;
            case INS_CHECK_VALIDITY: checkValidity(apdu); break;

            default: ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
//-----------------------------------------------------------------------------------------------------------   
    // Ham ho tro giai ma khoi du lieu 16 bytes duoc ma hoa bang TransportKey
    // Input: Buffer chua khoi ma hoa
    // Output: Copy du lieu giai ma duoc (Pin/Secret ro) vao buffer dich
    // Tra ve: Do dai thuc cua du lieu (duoc luu o byte dau tien cua khoi giai ma)
    private short decryptTransportData(byte[] input, short inputOff, byte[] output, short outputOff) {
        // Dung mode ECB de giai ma block 16 bytes
        cipherWrap.init(transportKeyObj, Cipher.MODE_DECRYPT);
        // Giai ma vao buffer tam
        cipherWrap.doFinal(input, inputOff, (short)16, temporary, (short)0);
        
        // Cau truc sau giai ma: [RealLength (1 byte)][Data (N bytes)][Padding...]
        short realLen = (short)(temporary[0] & 0xFF);
        
        if (realLen <= 0 || realLen > 15) ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        
        Util.arrayCopy(temporary, (short)1, output, outputOff, realLen);
        return realLen;
    }
//-----------------------------------------------------------------------------------------------------------   
    private void checkEmpty(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        buf[0] = (cardModel == null) ? (byte)0x01 : (byte)0x00;
        apdu.setOutgoingAndSend((short)0, (short)1);
    }
//-----------------------------------------------------------------------------------------------------------   
    private void checkValidity(APDU apdu) throws ISOException {
        if (cardModel == null) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
        byte[] buf = apdu.getBuffer();
        byte[] expiredData = cardModel.getExpired();
        short length = (short) expiredData.length; 
        
        if (length > 0) {
            Util.arrayCopyNonAtomic(expiredData, (short)0, buf, (short)0, length);
        } else {
            buf[0] = (byte) ' '; 
            length = 1;
        }
        
        apdu.setOutgoingAndSend((short) 0, length);
    }
//-----------------------------------------------------------------------------------------------------------   
 // lay duoc chuoi bi mat gui tu may tinh xuong
 // di so sanh
 // neu dung thi bat co hoat dong va cam di giai ma masterkey
    private void verifySecret(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short byteRead = apdu.setIncomingAndReceive(); 

        //  Bay gio nhan vao 1 block 16 bytes da ma hoa
        if (byteRead != 16) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        
        if (systemSecret == null) ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);

        // Giai ma de lay Secret ro
        // tempPinData o day duoc dung lam buffer trung gian chua Secret ro
        short secretLen = decryptTransportData(buffer, ISO7816.OFFSET_CDATA, tempPinData, (short)0);

        
        // check match voi du lieu ro vua giai ma
        if (systemSecret.match(tempPinData, (short)0, (byte)secretLen) == false) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        
        isSecretValidated = true;
        
        // systemSecret.getKEK() se tra ve key da duoc PBKDF2
        try {
            restoreMasterKeyFromSlot(systemSecret.getKEK(), encryptedMasterKey_Admin);
        } catch (Exception e) {
            isSecretValidated = false;
            ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        }
    }
//-----------------------------------------------------------------------------------------------------------   
    private void createPIN(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short lenReceived = apdu.setIncomingAndReceive();
        
        // Nhan vao 2 block 16 bytes: [Encrypted PIN (16)][Encrypted Secret (16)]
        if (lenReceived != 32) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		// bat dau doc tu vi tri du lieu
        short offset = ISO7816.OFFSET_CDATA;

        // 1. Giai ma PIN
        // Su dung tempPinData de chua PIN ro
        short pinLen = decryptTransportData(buf, offset, tempPinData, (short)0);
        offset += 16; 
        
        if (pinLen > 32 || pinLen < 0) ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        
        // Tao doi tuong PIN voi du lieu ro
        pin = new PIN(tempPinData, (short)0, (short)pinLen, NUMBER_RETRY, pbkdf2Engine);
        
        // 2. Giai ma System Secret
        // O day ta ghi de len tempPinData luon vi PIN da duoc new o tren.
        short secretLen = decryptTransportData(buf, offset, tempPinData, (short)0);
        offset += 16;
        
        if (secretLen > 16 || secretLen <= 0) ISOException.throwIt(ISO7816.SW_DATA_INVALID);
		// thuc hien luu vinh vien
        
        // Khoi tao systemSecret nhu mot PIN moi
        // Viec nay se tu dong Hash secret de luu tru, va tao Salt
        systemSecret = new PIN(tempPinData, (short)0, (short)secretLen, (byte)3, pbkdf2Engine);
		
        generateAndBackupMasterKey(pin.getKEK());
    }
 //-----------------------------------------------------------------------------------------------------------      
    private void updatePINDirect(APDU apdu) throws ISOException {
        byte[] buf = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        
        // DA SUA: Nhan vao 1 block 16 bytes ma hoa PIN moi
        if (lc != 16) ISOException.throwIt(ISO7816.SW_DATA_INVALID);
		// kiem tra xem masterKey co dang duoc mo hay khong
        if (!masterKeyObj.isInitialized()) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
        
        // Giai ma PIN moi
        short newPinLen = decryptTransportData(buf, ISO7816.OFFSET_CDATA, tempPinData, (short)0);

		//kiem tra xem Pin co trung voi Pin cu kh
        if (pin.check(tempPinData, (short)0, (byte)newPinLen)) {
            ISOException.throwIt(SW_PIN_SAME_AS_OLD);
        }

        pin.update(tempPinData, (short)0, (byte)newPinLen);
        // cap nhat lai khoa bao ve
        saveMasterKeyToSlot(pin.getKEK(), encryptedMasterKey_User);
    }
//-----------------------------------------------------------------------------------------------------------   
// sinh ra 1 masterkey 16 bytes ng u nhi�n
// nap vao ram(masterKeyObj)
// luu 2 dang ma hoa vao rom
    private void generateAndBackupMasterKey(byte[] userKek) {
        rng.generateData(masterKeyRAM, (short)0, (short)16);
        masterKeyObj.setKey(masterKeyRAM, (short)0);

        saveMasterKeyToSlot(userKek, encryptedMasterKey_User);
        saveMasterKeyToSlot(systemSecret.getKEK(), encryptedMasterKey_Admin);
    }
//-----------------------------------------------------------------------------------------------------------   
// ham boc khoa
    private void saveMasterKeyToSlot(byte[] keyBytes, byte[] destBuffer) {
    	// dua du lieu lam chia khoa vao
        wrappingKeyObj.setKey(keyBytes, (short)0);
        // ma hoa
        // cai nay dung ECB vi no co 16byte thoi, lam nhu nay cho de
        cipherWrap.init(wrappingKeyObj, Cipher.MODE_ENCRYPT);
        cipherWrap.doFinal(masterKeyRAM, (short)0, (short)16, destBuffer, (short)0);
    }
//-----------------------------------------------------------------------------------------------------------
// ham nay de mo khoa   
    private void restoreMasterKeyFromSlot(byte[] keyBytes, byte[] srcBuffer) {
        wrappingKeyObj.setKey(keyBytes, (short)0);
        cipherWrap.init(wrappingKeyObj, Cipher.MODE_DECRYPT);
        cipherWrap.doFinal(srcBuffer, (short)0, (short)16, masterKeyRAM, (short)0);
        // dua vao RAM de su dung
        masterKeyObj.setKey(masterKeyRAM, (short)0);
    }
//-----------------------------------------------------------------------------------------------------------   
// lam sach cho an toan
    private void clearSession() {
        Util.arrayFillNonAtomic(masterKeyRAM, (short)0, (short)16, (byte)0x00);
        masterKeyObj.clearKey();
    }
//-----------------------------------------------------------------------------------------------------------  
// ma hoa cua mode CBC, c� iv ramdom 
    private void encryptInfo(byte[] input, short inOff, short len, byte[] output, short outOff) {
        if (!masterKeyObj.isInitialized()) ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        if (len > 32) ISOException.throwIt(ISO7816.SW_DATA_INVALID);

        rng.generateData(iv, (short)0, (short)16);
        Util.arrayCopyNonAtomic(iv, (short)0, output, outOff, (short)16);

        Util.arrayFillNonAtomic(cipherBuffer, (short)0, (short)32, (byte)0x00);
        Util.arrayCopyNonAtomic(input, inOff, cipherBuffer, (short)0, len);
        
        cipherData.init(masterKeyObj, Cipher.MODE_ENCRYPT, iv, (short) 0, (short) 16);
        cipherData.doFinal(cipherBuffer, (short) 0, (short)32, output, (short)(outOff + 16));
    }
//-----------------------------------------------------------------------------------------------------------   
// giai ma cua CBC	
    private short decryptInfoField(byte[] input, byte[] output, short outOffset) {
        if (!masterKeyObj.isInitialized()) return 0;
        // tach lay iv
        Util.arrayCopyNonAtomic(input, (short)0, iv, (short)0, (short)16);
        // tach du lieu bao mnat
        Util.arrayCopyNonAtomic(input, (short)16, cipherBuffer, (short)0, (short)32);
        
        cipherData.init(masterKeyObj, Cipher.MODE_DECRYPT, iv, (short) 0, (short) 16);
        cipherData.doFinal(cipherBuffer, (short) 0, (short)32, output, outOffset);
        // loai bo padding
        short pointer = (short) (outOffset + 31);
        while (pointer >= outOffset && output[pointer] == (byte) 0x00) {
            pointer--;
        }
        if (pointer < outOffset) return 0;
        return (short) (pointer - outOffset + 1);
    }
//-----------------------------------------------------------------------------------------------------------    
// cai nay de ma hoa ECB ne   
    private void decryptECBBlock(byte[] input, short inOff, short len, byte[] output, short outOff) {
        if (!masterKeyObj.isInitialized()) return;
        cipherWrap.init(masterKeyObj, Cipher.MODE_DECRYPT); 
        cipherWrap.doFinal(input, inOff, len, output, outOff);
    }
//-----------------------------------------------------------------------------------------------------------  
// no tach tung truong theo form [Length][Data][Length][Data]..., doc xem length l� may de lay data
    private void createCard(APDU apdu) throws ISOException {
        if (!masterKeyObj.isInitialized()) ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);

        byte[] buf = apdu.getBuffer();
        apdu.setIncomingAndReceive();
        // the bi ngat thi huy
        JCSystem.beginTransaction();
        cardModel = new CardModel();
        
        byte offset = ISO7816.OFFSET_CDATA;
        short length;

        length = (short) (buf[offset] & 0xFF);
        encryptInfo(buf, (short)(offset + 1), length, temporary, (short)0);
        cardModel.setId(buf, (short)(offset + 1), length);
        
        offset += (short) (length + 1);

        length = (short) (buf[offset] & 0xFF);
        encryptInfo(buf, (short)(offset + 1), length, temporary, (short)0);
        cardModel.setIdCard(temporary, (short) 0, ENCRYPTED_INFO_SIZE);
        offset += (short) (length + 1);

        length = (short) (buf[offset] & 0xFF);
        encryptInfo(buf, (short)(offset + 1), length, temporary, (short)0);
        cardModel.setFullName(temporary, (short) 0, ENCRYPTED_INFO_SIZE);
        offset += (short) (length + 1);

        length = (short) (buf[offset] & 0xFF);
        encryptInfo(buf, (short)(offset + 1), length, temporary, (short)0);
        cardModel.setDateOfBirth(temporary, (short) 0, ENCRYPTED_INFO_SIZE);
        offset += (short) (length + 1);

        length = (short) (buf[offset] & 0xFF);
        encryptInfo(buf, (short)(offset + 1), length, temporary, (short)0);
        cardModel.setAddress(temporary, (short) 0, ENCRYPTED_INFO_SIZE);
        offset += (short) (length + 1);

        length = (short) (buf[offset] & 0xFF);
        encryptInfo(buf, (short)(offset + 1), length, temporary, (short)0);
        cardModel.setPhoneNumber(temporary, (short) 0, ENCRYPTED_INFO_SIZE);


        Util.arrayFillNonAtomic(temporary, (short) 0, (short) 64, (byte) 0x00);
        temporary[0] = (byte) '0';
        encryptInfo(temporary, (short) 0, (short) 1, temporary, (short)0);
        cardModel.setMoney(temporary, (short) 0, ENCRYPTED_INFO_SIZE);

        
        Util.arrayFillNonAtomic(temporary, (short) 0, (short) 64, (byte) 0x00);
        temporary[0] = (byte) '-';
        cardModel.setExpired(temporary, (short) 0, (short) 1);
        // -----------------------------
		// hoan tat
        JCSystem.commitTransaction();
		// tu sinh cap key rsa
        KeyPair keyPair = generateKeyPair();
        privateKey = (RSAPrivateKey) keyPair.getPrivate();
        publicKey = (RSAPublicKey) keyPair.getPublic();
        
        //  Ma hoa Private Key (Exponent) bang MasterKey (ECB)
        // tempMessage du to de chua key 128 bytes
        short expLen = privateKey.getExponent(tempMessage, (short)0);
        cipherWrap.init(masterKeyObj, Cipher.MODE_ENCRYPT);
        cipherWrap.doFinal(tempMessage, (short)0, expLen, encryptedPrivateKeyExp, (short)0);
        
        // Xoa key ro de bao mat
        privateKey.clearKey();

        short pubKeyLen = serializePublicKey(publicKey, buf, (short) 0);
        apdu.setOutgoingAndSend((short) 0, pubKeyLen);
    }
//-----------------------------------------------------------------------------------------------------------   
    private void updateInfo(APDU apdu) throws ISOException {
        byte[] buf = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        if (!checkAdminForCurrentCommandFromBuffer(buf, ISO7816.OFFSET_CDATA, lc)) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        applyInfoFromBuffer(buf, ISO7816.OFFSET_CDATA, lc);
    }
//----------------------------------------------------------------------------------------------------------- 
// cap nhat thong gan giong tao thong tin  
    private void applyInfoFromBuffer(byte[] buffer, short startOffset, short totalLen) {
        short offset = startOffset;
        short msgLen = Util.getShort(buffer, offset); offset += (short)(2 + msgLen);
        short sigLen = Util.getShort(buffer, offset); offset += (short)(2 + sigLen);
        short length;

        length = (short) (buffer[offset++] & 0xFF);
        encryptInfo(buffer, offset, length, temporary, (short)0);
        cardModel.setFullName(temporary, (short)0, ENCRYPTED_INFO_SIZE);
        offset += length;
        
        length = (short) (buffer[offset++] & 0xFF);
        encryptInfo(buffer, offset, length, temporary, (short)0);
        cardModel.setDateOfBirth(temporary, (short)0, ENCRYPTED_INFO_SIZE);
        offset += length;
        
        length = (short) (buffer[offset++] & 0xFF);
        encryptInfo(buffer, offset, length, temporary, (short)0);
        cardModel.setAddress(temporary, (short)0, ENCRYPTED_INFO_SIZE);
        offset += length;
        
        length = (short) (buffer[offset++] & 0xFF);
        encryptInfo(buffer, offset, length, temporary, (short)0);
        cardModel.setPhoneNumber(temporary, (short)0, ENCRYPTED_INFO_SIZE);
    }
//-----------------------------------------------------------------------------------------------------------   
// ham nap tien vao cua admin nen can check xem co phai day la admin kh
    private void updateMoney(APDU apdu) throws ISOException {
        byte[] buf = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        if (!checkAdminForCurrentCommandFromBuffer(buf, ISO7816.OFFSET_CDATA, lc)) {
              ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        short offset = ISO7816.OFFSET_CDATA;
        short msgLen = Util.getShort(buf, offset); offset += (short)(2 + msgLen);
        short sigLen = Util.getShort(buf, offset); offset += (short)(2 + sigLen);
        short moneyLen = (short)(lc - (offset - ISO7816.OFFSET_CDATA));
        
        encryptInfo(buf, offset, moneyLen, temporary, (short)0);
        cardModel.setMoney(temporary, (short)0, ENCRYPTED_INFO_SIZE);
    }
//-----------------------------------------------------------------------------------------------------------  
// may ham thanh toan, ky xong cho xac thuc thanh cong thi moi thanh toan
// masterkey chua duoc giai ma thi kh thanh toan dc dau 
    private void updateMoneyDirect(APDU apdu) throws ISOException {
        byte[] buf = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        
        if (lc == 0) ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        
        if (!masterKeyObj.isInitialized()) {
              ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }

        encryptInfo(buf, ISO7816.OFFSET_CDATA, lc, temporary, (short)0);
        cardModel.setMoney(temporary, (short)0, ENCRYPTED_INFO_SIZE);
    }
//----------------------------------------------------------------------------------------------------------- 
// cap nhat han, phuc vu cho viec ra vao cong nen khong can ma hoa  
    private void updateExpired(APDU apdu) throws ISOException {
        byte[] buf = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        if (lc == 0) ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        cardModel.setExpired(buf, ISO7816.OFFSET_CDATA, lc);
    }
//-----------------------------------------------------------------------------------------------------------   
    private void updateAvatar(APDU apdu) {
        if (cardModel == null) ISOException.throwIt(ISO7816.SW_COMMAND_NOT_ALLOWED);
        byte[] buf = apdu.getBuffer();
        short length = apdu.setIncomingAndReceive();
        // p1 va p2 ket hop lai de thanh 1 so
        short chunkOffset = Util.makeShort((byte)(buf[ISO7816.OFFSET_P1] & 0xFF), (byte)(buf[ISO7816.OFFSET_P2] & 0xFF));
        if (length == 0) return;
		// tinh xem can bao nhieu block
        short numBlocks = (short)((length + 15) / 16);
        short encryptedLength = (short)(numBlocks * 16);
        
        if ((short)(chunkOffset + encryptedLength) > MAX_SIZE_AVATAR) 
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);

        byte[] avatarData = cardModel.getAvatar().getData();
        short srcPos = ISO7816.OFFSET_CDATA;
        short dstPos = chunkOffset;
        
        short remaining = length;
        
        cipherWrap.init(masterKeyObj, Cipher.MODE_ENCRYPT);
		// dung mode ecb ma hoa tung khoi 16bytes
        for (short i = 0; i < numBlocks; i++) {
            Util.arrayFillNonAtomic(temporary, (short)0, (short)16, (byte)0x00);
            
            short bytesToCopy = (remaining > 16) ? (short)16 : remaining;
            Util.arrayCopy(buf, srcPos, temporary, (short)0, bytesToCopy);
            
            cipherWrap.doFinal(temporary, (short)0, (short)16, avatarData, dstPos);
            
            srcPos += bytesToCopy;
            dstPos += 16;
            remaining -= bytesToCopy;
        }

        short newEnd = (short)(chunkOffset + encryptedLength);
        if (newEnd > cardModel.getAvatar().getSize()) {
            cardModel.getAvatar().setSize(newEnd);
        }
    }
    // mo rong
    private void updateAvatarL(APDU apdu) {
        if (cardModel == null) ISOException.throwIt(ISO7816.SW_COMMAND_NOT_ALLOWED);
        if (!masterKeyObj.isInitialized()) ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        
        byte[] buf = apdu.getBuffer();
        
        // apdu.getIncomingLength() tra ve tong do dai gui xuong
        short totalLen = apdu.getIncomingLength(); 
        
        if (totalLen > MAX_SIZE_AVATAR) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        // Du lieu anh duoc luu vao trong nay
        byte[] avatarData = cardModel.getAvatar().getData();
       
        cipherWrap.init(masterKeyObj, Cipher.MODE_ENCRYPT);
        
        short bytesRead = 0;
        short storageOffset = 0;
        
        // lay du lieu co san trong buffer adpu
        short recvLen = apdu.setIncomingAndReceive();
        short bufOffset = ISO7816.OFFSET_CDATA;
        
       
        while (bytesRead < totalLen) {
            
            // chia 16byte de xu ly
            short processedInLoop = 0;
            while (processedInLoop < recvLen) {
              
                short remain = (short)(recvLen - processedInLoop);
                short chunkSize = (remain > 16) ? (short)16 : remain;
                
                Util.arrayFillNonAtomic(temporary, (short)0, (short)16, (byte)0x00); // Padding 0
                Util.arrayCopy(buf, (short)(bufOffset + processedInLoop), temporary, (short)0, chunkSize);
                
                cipherWrap.doFinal(temporary, (short)0, (short)16, avatarData, storageOffset);
                
                storageOffset += 16; 
                processedInLoop += chunkSize;
            }
            
            bytesRead += recvLen;
            
            if (bytesRead < totalLen) {
                recvLen = apdu.receiveBytes((short)0);
                bufOffset = (short)0;
            }
        }
        
        cardModel.getAvatar().setSize(storageOffset);
    }
//-----------------------------------------------------------------------------------------------------------   
    private void getAvatar(APDU apdu) {
        if (cardModel == null || cardModel.getAvatar() == null) ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        
        byte[] encryptedData = cardModel.getAvatar().getData();
        short encryptedSize = cardModel.getAvatar().getSize();
        if (encryptedSize == 0) {
             apdu.setOutgoingAndSend((short)0, (short)0); return;
        }
		// tinh so khoi can xu ly
        short numBlocks = (short)(encryptedSize / 16);
        if (numBlocks == 0) { apdu.setOutgoingAndSend((short)0, (short)0); return; }
		// giai ma khoi cuoi cung de xem co bao nhieu bytes
        decryptECBBlock(encryptedData, (short)((numBlocks - 1) * 16), (short)16, temporary, (short)0);
        
        short paddingCount = 0;
        for (short i = 15; i >= 0; i--) {
            if (temporary[i] == 0) paddingCount++; else break;
        }
        // kich thuoc that sau khi b  cac byte thua
        short actualSize = (short)(encryptedSize - paddingCount);
		// chuan bi gui du lieu ra ngoai
        apdu.setOutgoing();
        apdu.setOutgoingLength(actualSize);
        
        byte[] buf = apdu.getBuffer();
        short srcOffset = 0;
        short totalSent = 0;
        short bufOffset = 0;
		// giai ma va gui du lieu
        for (short i = 0; i < numBlocks; i++) {
             decryptECBBlock(encryptedData, srcOffset, (short)16, temporary, (short)0);
             
             short remaining = (short)(actualSize - totalSent);
             short bytesToCopy = remaining > 16 ? (short)16 : remaining;
             
             Util.arrayCopy(temporary, (short)0, buf, bufOffset, bytesToCopy);
             
             bufOffset += bytesToCopy;
             totalSent += bytesToCopy;
             srcOffset += 16;
             
             if (bufOffset >= 200 || totalSent >= actualSize) {
                 apdu.sendBytesLong(buf, (short)0, bufOffset);
                 bufOffset = 0;
             }
             if (totalSent >= actualSize) break;
        }
    }
    // mo rong
    private void getAvatarL(APDU apdu) {
        if (cardModel == null || cardModel.getAvatar() == null) 
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        if (!masterKeyObj.isInitialized()) 
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
            
        byte[] encryptedData = cardModel.getAvatar().getData();
        short encryptedSize = cardModel.getAvatar().getSize();
        
        if (encryptedSize == 0) {
             apdu.setOutgoingAndSend((short)0, (short)0); 
             return;
        }

        // Tinh kich thuoc that khi loai bo padding
        // Decrypt block cuoi cung (16 bytes) vao temporary e em padding
        cipherWrap.init(masterKeyObj, Cipher.MODE_DECRYPT);
        cipherWrap.doFinal(encryptedData, (short)(encryptedSize - 16), (short)16, temporary, (short)0);
        
        short paddingCount = 0;
        
        for (short i = 15; i >= 0; i--) {
            if (temporary[i] == 0) paddingCount++; else break;
        }
        
        short actualSize = (short)(encryptedSize - paddingCount);


        apdu.setOutgoing();
        apdu.setOutgoingLength(actualSize);
        
        short sentBytes = 0;
        short currentOffset = 0;
        
        // V�ng lp gii m� v� gi dn (Stream sending)
        while (sentBytes < actualSize) {
            // Gii m� 1 block 16 bytes t Persistent v�o RAM
            cipherWrap.init(masterKeyObj, Cipher.MODE_DECRYPT);
            cipherWrap.doFinal(encryptedData, currentOffset, (short)16, temporary, (short)0);
            
            // T�nh s byte thc t cn gi trong block n�y (tr padding nu l� block cui)
            short bytesToSend = 16;
            if ((short)(sentBytes + 16) > actualSize) {
                bytesToSend = (short)(actualSize - sentBytes);
            }
            
            // Gi on d liu � gii m�
            apdu.sendBytesLong(temporary, (short)0, bytesToSend);
            
            sentBytes += bytesToSend;
            currentOffset += 16;
        }
    }
//-----------------------------------------------------------------------------------------------------------   
    private void getCard(APDU apdu) throws ISOException {
        if (cardModel == null) return;
        byte[] buf = apdu.getBuffer();
        byte offset = 0;
        
        buf[offset] = (byte)decryptInfoField(cardModel.getIdCard(), buf, (short)(offset + 1));
        offset += (short) (buf[offset] + 1);
        buf[offset] = (byte)decryptInfoField(cardModel.getFullName(), buf, (short)(offset + 1));
        offset += (short) (buf[offset] + 1);
        buf[offset] = (byte)decryptInfoField(cardModel.getDateOfBirth(), buf, (short)(offset + 1));
        offset += (short) (buf[offset] + 1);
        buf[offset] = (byte)decryptInfoField(cardModel.getAddress(), buf, (short)(offset + 1));
        offset += (short) (buf[offset] + 1);
        buf[offset] = (byte)decryptInfoField(cardModel.getPhoneNumber(), buf, (short)(offset + 1));
        offset += (short) (buf[offset] + 1);
        buf[offset] = (byte)decryptInfoField(cardModel.getMoney(), buf, (short)(offset + 1));
        offset += (short) (buf[offset] + 1);
        
        
        byte[] expiredData = cardModel.getExpired();
        short expiredLen = (short) expiredData.length;
        
        buf[offset] = (byte) expiredLen;
        if (expiredLen > 0) {
            Util.arrayCopyNonAtomic(expiredData, (short)0, buf, (short)(offset + 1), expiredLen);
        }
        apdu.setOutgoingAndSend((short) 0, (short) (offset + expiredLen + 1));
        
    }
//-----------------------------------------------------------------------------------------------------------      
    private void getMoney(APDU apdu) throws ISOException {
        byte[] buf = apdu.getBuffer();
        short length = decryptInfoField(cardModel.getMoney(), buf, (short) 0);
        apdu.setOutgoingAndSend((short) 0, length);
    }
//-----------------------------------------------------------------------------------------------------------   
    private void verifyPIN(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short dataLen = apdu.setIncomingAndReceive();
        byte offset = ISO7816.OFFSET_CDATA;

        if (pin == null) ISOException.throwIt(ISO7816.SW_COMMAND_NOT_ALLOWED);
        
        // DA SUA: Nhan vao 1 block 16 bytes ma hoa
        if (dataLen != 16) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        
        // Giai ma PIN
        short pinLen = decryptTransportData(buf, offset, tempPinData, (short)0);

		// neu pin dung kich hoat masterkey
        if (pin.match(tempPinData, (short)0, (byte)pinLen)) {
            try {
                restoreMasterKeyFromSlot(pin.getKEK(), encryptedMasterKey_User);
            } catch (Exception e) {
                ISOException.throwIt(ISO7816.SW_DATA_INVALID);
            }
            return;
        }
        buf[ISO7816.OFFSET_CDATA] = pin.getTriesRemaining();
        apdu.setOutgoingAndSend(ISO7816.OFFSET_CDATA, (short) 1);
        ISOException.throwIt(ISO7816.SW_WRONG_DATA);
    }
//-----------------------------------------------------------------------------------------------------------   
    private void updatePIN(APDU apdu) throws ISOException {
        if (!masterKeyObj.isInitialized()) ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        byte[] buf = apdu.getBuffer();
        apdu.setIncomingAndReceive();
        // kiem tra xem du quyen kh	
        short lc = (short)(buf[ISO7816.OFFSET_LC] & 0xFF);
        if (!checkAdminForCurrentCommandFromBuffer(buf, ISO7816.OFFSET_CDATA, lc)) {
              ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        // boc du lieu
        short offset = ISO7816.OFFSET_CDATA;
        short msgLen = Util.getShort(buf, offset); offset += (short)(2 + msgLen);
        short sigLen = Util.getShort(buf, offset); offset += (short)(2 + sigLen);
        
        // Data sau chu ky bay gio la 1 block 16 bytes ma hoa PIN moi
        // Structure cu: [pinLen][pin] -> Structure moi: [16 bytes Encrypted PIN]
        
        // Giai ma PIN moi
        short pinLen = decryptTransportData(buf, offset, tempPinData, (short)0);

        pin.update(tempPinData, (short)0, (byte)pinLen);
        saveMasterKeyToSlot(pin.getKEK(), encryptedMasterKey_User);
    }
//-----------------------------------------------------------------------------------------------------------      
    private void resetPIN(APDU apdu) throws ISOException {
        byte[] buf = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        if (!checkAdminForCurrentCommandFromBuffer(buf, ISO7816.OFFSET_CDATA, lc)) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        pin.resetPin();
    }
//-----------------------------------------------------------------------------------------------------------       
    private void getPinTries(APDU apdu) {
        if(pin == null) ISOException.throwIt(ISO7816.SW_COMMAND_NOT_ALLOWED);
        byte[] buf = apdu.getBuffer();
        buf[0] = pin.getTriesRemaining();
        apdu.setOutgoingAndSend((short)0, (short)1);
    }
//-----------------------------------------------------------------------------------------------------------   
    private void entryGate(APDU apdu) {
        if (cardModel == null) ISOException.throwIt(ISO7816.SW_COMMAND_NOT_ALLOWED);
        byte[] buf = apdu.getBuffer();
        apdu.setIncomingAndReceive();
        if (cardModel.getIsInside()) {
             buf[0] = 0; apdu.setOutgoingAndSend((short)0, (short)1); return;
        }
        cardModel.setEntryTime(buf, ISO7816.OFFSET_CDATA, (short)8);
        cardModel.setIsInside(true);
        buf[0] = 1; apdu.setOutgoingAndSend((short)0, (short)1);
    }
//-----------------------------------------------------------------------------------------------------------   
    private void exitGate(APDU apdu) {
        if (cardModel == null) ISOException.throwIt(ISO7816.SW_COMMAND_NOT_ALLOWED);
        byte[] buf = apdu.getBuffer();
        if (!cardModel.getIsInside()) {
             buf[0] = 0; apdu.setOutgoingAndSend((short)0, (short)1); return;
        }
        byte[] time = cardModel.getEntryTime();
        buf[0] = 1; Util.arrayCopyNonAtomic(time, (short)0, buf, (short)1, (short)8);
        cardModel.setIsInside(false);
        Util.arrayFillNonAtomic(time, (short)0, (short)8, (byte)0);
        apdu.setOutgoingAndSend((short)0, (short)9);
    }
//-----------------------------------------------------------------------------------------------------------   
    private void getEntryState(APDU apdu) {
        if (cardModel == null) ISOException.throwIt(ISO7816.SW_COMMAND_NOT_ALLOWED);
        byte[] buf = apdu.getBuffer();
        buf[0] = cardModel.getIsInside() ? (byte)1 : (byte)0;
        Util.arrayCopyNonAtomic(cardModel.getEntryTime(), (short)0, buf, (short)1, (short)8);
        apdu.setOutgoingAndSend((short)0, (short)9);
    }
//-----------------------------------------------------------------------------------------------------------       
    private void getDbId(APDU apdu) {
        if (cardModel == null) ISOException.throwIt(ISO7816.SW_COMMAND_NOT_ALLOWED);
        byte[] buf = apdu.getBuffer();
        byte[] id = cardModel.getId();
        Util.arrayCopyNonAtomic(id, (short)0, buf, (short)0, (short)id.length);
        apdu.setOutgoingAndSend((short)0, (short)id.length);
    }
//-----------------------------------------------------------------------------------------------------------      
    private void getCardId(APDU apdu) {
        if (cardModel == null) ISOException.throwIt(ISO7816.SW_COMMAND_NOT_ALLOWED);
        byte[] buf = apdu.getBuffer();
        short len = decryptInfoField(cardModel.getIdCard(), buf, (short)0);
        apdu.setOutgoingAndSend((short)0, len);
    }
//-----------------------------------------------------------------------------------------------------------   
    private void setBackendPublicKey(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();
        if (len > 200) ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        cardModel.setBackendPublicKey(buf, ISO7816.OFFSET_CDATA, len);
        cachedBackendPubKey = null;
    }
//-----------------------------------------------------------------------------------------------------------
//-----------------------------------------------------------------------------------------------------------
//-----------------------------------------------------------------------------------------------------------     
// cai nay de tao chu ky  
    private void createSignature(APDU apdu) {
    	// nhan du lieu tu may tinh gui xuong de ky
         byte[] buf = apdu.getBuffer();
         short len = apdu.setIncomingAndReceive();
         
         // DA SUA: Giai ma Private Key truoc khi ky
         if (!masterKeyObj.isInitialized()) ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
         
         // 1. Giai ma Exponent vao tempMessage
         cipherWrap.init(masterKeyObj, Cipher.MODE_DECRYPT);
         cipherWrap.doFinal(encryptedPrivateKeyExp, (short)0, (short)128, tempMessage, (short)0);
         
         // 2. Lay Modulus tu PublicKey (vi modulus khong bi xoa) vao cuoi buffer
         // RSA 1024 bit modulus = 128 bytes
         short modLen = publicKey.getModulus(tempMessage, (short)128); 
         
         // 3. Khoi phuc Private Key
         privateKey.setModulus(tempMessage, (short)128, modLen);
         privateKey.setExponent(tempMessage, (short)0, (short)128);
         
         // 4. Ky
         signature.init(privateKey, Signature.MODE_SIGN);
         short sigLen = signature.sign(buf, ISO7816.OFFSET_CDATA, len, signatureBuffer, (short)0);
         
         // 5. Xoa key sau khi dung xong de bao mat
         privateKey.clearKey();
         Util.arrayFillNonAtomic(tempMessage, (short)0, (short)256, (byte)0);
         
         apdu.setOutgoing();
         apdu.setOutgoingLength(sigLen);
         apdu.sendBytesLong(signatureBuffer, (short)0, sigLen);
    }
//-----------------------------------------------------------------------------------------------------------  
// cai nay de lay ra may thong tin chua giai ma de neu co hoi thi cho co xem     
    private void getCardNO(APDU apdu) {
        if (cardModel == null) ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        byte[] buf = apdu.getBuffer();
        short offset = 0;
        offset = copyAttr(cardModel.getIdCard(), buf, offset);
        offset = copyAttr(cardModel.getFullName(), buf, offset);
        offset = copyAttr(cardModel.getDateOfBirth(), buf, offset);
        apdu.setOutgoingAndSend((short)0, offset);
    }
//-----------------------------------------------------------------------------------------------------------      
// ham nay la ham chung cho may may cai truong 
    private short copyAttr(byte[] data, byte[] buf, short offset) {
        if (data == null) { buf[offset++] = 0; return offset; }
        short len = decryptInfoField(data, buf, (short)(offset + 1));
        buf[offset] = (byte)len;
        return (short)(offset + 1 + len);
    }
//-----------------------------------------------------------------------------------------------------------   
//-----------------------------------------------------------------------------------------------------------   
// Kiem tra chu ky cua Admin tu Backend (BE)
// Input: buffer chua [msgLen(2)][message][sigLen(2)][signature]
// Return: true neu chu ky hop le VA la Admin role
private boolean checkAdminForCurrentCommandFromBuffer(byte[] buf, short startOffset, short len) {
    short offset = startOffset;
    
    // Doc messageLen (2 bytes dau tien)
    if ((short)(offset + 2 - startOffset) > len) return false;
    short messageLen = Util.getShort(buf, offset); 
    offset += 2;
    
    // Validate messageLen
    if(messageLen <= 0 || messageLen > 256 || (short)(offset + messageLen - startOffset) > len) 
        return false;
    
    // Copy message vao buffer tam tempMessage
    Util.arrayFillNonAtomic(tempMessage, (short)0, messageLen, (byte)0);
    Util.arrayCopy(buf, offset, tempMessage, (short)0, messageLen);
    offset += messageLen;
    
    // Doc sigLen (2 bytes tiep theo)
    if ((short)(offset + 2 - startOffset) > len) return false;
    short sigLen = Util.getShort(buf, offset); 
    offset += 2;
    
    // Validate sigLen
    if(sigLen <= 0 || sigLen > 128 || (short)(offset + sigLen - startOffset) > len) 
        return false;
    
    // Copy signature vao buffer tam tempSignature
    Util.arrayFillNonAtomic(tempSignature, (short)0, sigLen, (byte)0);
    Util.arrayCopy(buf, offset, tempSignature, (short)0, sigLen);
    
    // Kiem tra chu ky hop le VA co role Admin khong
    return verifySignatureFromBE(tempMessage, messageLen, tempSignature, sigLen) 
           && isAdminRole(tempMessage, messageLen);
}

//-----------------------------------------------------------------------------------------------------------   
// Xac thuc chu ky RSA tu Backend
// Su dung Public Key da luu tren the (cachedBackendPubKey)
// Return: true neu chu ky khop voi message
private boolean verifySignatureFromBE(byte[] message, short messageLen, byte[] signatureData, short sigLen) {
    try {
        // Neu chua co cached key thi khoi tao tu cardModel
        if (cachedBackendPubKey == null) {
            if (cardModel == null) return false;
            
            // Doc Backend Public Key da luu tren the
            byte[] pubKeyBytes = cardModel.getBackendPublicKey();
            short pos = 0;
            
            // Doc Modulus: [modLen(1)][modulus(N bytes)]
            short modLen = (short) (pubKeyBytes[pos++] & 0xFF);
            
            // Tao RSA Public Key object (512 bit)
            cachedBackendPubKey = (RSAPublicKey) KeyBuilder.buildKey(
                KeyBuilder.TYPE_RSA_PUBLIC, 
                KeyBuilder.LENGTH_RSA_512, 
                false
            );
            cachedBackendPubKey.setModulus(pubKeyBytes, pos, modLen);
            pos += modLen;
            
            // Doc Exponent: [expLen(1)][exponent(N bytes)]
            short expLen = (short) (pubKeyBytes[pos++] & 0xFF);
            cachedBackendPubKey.setExponent(pubKeyBytes, pos, expLen);
            
            // Tao Signature verification engine
            cachedSigVerify = Signature.getInstance(Signature.ALG_RSA_SHA_PKCS1, false);
        }
        
        // Khoi tao engine voi Public Key
        cachedSigVerify.init(cachedBackendPubKey, Signature.MODE_VERIFY);
        
        // Verify: Hash(message) == Decrypt(signature) ?
        return cachedSigVerify.verify(message, (short)0, messageLen, signatureData, (short)0, sigLen);
        
    } catch (Exception e) { 
        return false; 
    }
}

//-----------------------------------------------------------------------------------------------------------       
// Kiem tra xem message co role Admin khong
// Role Admin duoc luu o byte CUOI CUNG cua message
// ROLE_ADMIN = 0x01
private boolean isAdminRole(byte[] message, short messageLen) {
    return messageLen >= 1 && message[(short)(messageLen - 1)] == ROLE_ADMIN;
}

//-----------------------------------------------------------------------------------------------------------      
// Lay Public Key cua the (tu RSA key pair da tao)
// Ham nay tao MOI key pair moi lan goi
// Nen dung key pair da luu tu luc createCard()
// private void getPublicKey(byte[] buf, APDU apdu) {
    // // TAO MOI key pair (SAI THIET KE)
    // KeyPair keyPair = generateKeyPair();
    // privateKey = (RSAPrivateKey) keyPair.getPrivate();
    // publicKey = (RSAPublicKey) keyPair.getPublic();
    
    // // Serialize Public Key ra buffer
    // short length = serializePublicKey(publicKey, buf, (short) 0);
    
    // // Gui tra ve may tinh
    // apdu.setOutgoingAndSend((short) 0, length);
// }

//-----------------------------------------------------------------------------------------------------------       
// Tao cap khoa RSA 1024-bit
// Private Key: dung de ky du lieu
// Public Key: dung de xac thuc chu ky (gui cho Backend)
public KeyPair generateKeyPair() {
    KeyPair keyPair = new KeyPair(KeyPair.ALG_RSA, KeyBuilder.LENGTH_RSA_1024);
    keyPair.genKeyPair(); // Sinh khoa ngau nhien
    return keyPair;
}

//-----------------------------------------------------------------------------------------------------------      
// Dong goi Public Key thanh byte array de gui ra ngoai
// Format: [expLen(2)][exponent][modLen(2)][modulus]
// Return: Tong so byte da ghi
public short serializePublicKey(RSAPublicKey key, byte[] buffer, short offset) {
    // Lay Exponent (thong thuong la 65537 = 0x010001, 3 bytes)
    short exponentLength = key.getExponent(buffer, (short) (offset + 2));
    
    // Lay Modulus (RSA-1024 => 128 bytes)
    short modulusLength = key.getModulus(buffer, (short) (offset + 2 + exponentLength + 2));
    
    // Ghi do dai Exponent vao 2 bytes dau
    Util.setShort(buffer, offset, exponentLength);
    
    // Ghi do dai Modulus vao 2 bytes sau Exponent
    Util.setShort(buffer, (short) (offset + 2 + exponentLength), modulusLength);
    
    // Tong: 2 + expLen + 2 + modLen
    return (short) (4 + exponentLength + modulusLength);
}
}