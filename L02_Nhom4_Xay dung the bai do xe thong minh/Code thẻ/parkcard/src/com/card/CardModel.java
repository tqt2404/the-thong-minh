package com.card;

import javacard.framework.Util;
import javacard.framework.ISOException;
import javacard.framework.ISO7816;

public class CardModel {
    
    // K�ch thc mi cho c�c trng d liu c m� h�a
    // 16 bytes (IV ngu nhi�n) + 32 bytes (D liu m� h�a) = 48 bytes
    private static final short ENCRYPTED_SIZE = 48;

    private final byte[] id;           // ID thng kh�ng m� h�a hoc m� h�a ri�ng, gi 32 hoc tng t�y �
    private final byte[] idCard;       
    private final byte[] fullName;
    private final byte[] dateofBirth;
    private final byte[] address;
    private final byte[] phoneNumber;
    private final byte[] money;
    private final byte[] expired;
    
    private final Avatar avatar;

    private final byte[] backendPublicKey;
    private short backendPublicKeyLen = 0;
    
    private boolean isInside;
    private final byte[] entryTime;

    public CardModel() {
        // ID card (c�i n�y l� ID nh danh trong DB, code c bn  32)
        id = new byte[32];            
        
        // C�c trng th�ng tin c� nh�n (Cn 48 bytes  cha IV + Cipher)
        idCard = new byte[ENCRYPTED_SIZE];
        fullName = new byte[ENCRYPTED_SIZE];
        dateofBirth = new byte[ENCRYPTED_SIZE];
        address = new byte[ENCRYPTED_SIZE];
        phoneNumber = new byte[ENCRYPTED_SIZE];
        money = new byte[ENCRYPTED_SIZE];
        expired = new byte[ENCRYPTED_SIZE];
        
        // Avatar gi nguy�n logic c (� x l� trong Applet)
        avatar = new Avatar((short)8192);
        
        // RSA 1024 Key (~128 bytes + exponent) -> 200 bytes l� d d
        backendPublicKey = new byte[200];
        
        isInside = false;
        entryTime = new byte[8];
        Util.arrayFillNonAtomic(entryTime, (short)0, (short)8, (byte)0x00);
    }

    public void setBackendPublicKey(byte[] buffer, short offset, short length){
        if (length > backendPublicKey.length) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        Util.arrayCopy(buffer, offset, backendPublicKey, (short)0, length);
        backendPublicKeyLen = length;
    }
    public byte[] getBackendPublicKey(){
        return backendPublicKey;
    }
    public short getBackendPublicKeyLen() {
        return backendPublicKeyLen;
    }

    public void setId(byte[] buffer, short offset, short length){
        // Kim tra  d�i  tr�nh li tr�n mng
        if (length > id.length) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        Util.arrayCopy(buffer, offset, id, (short) 0, length);
    }
    public byte[] getId(){
        return id;
    }

    // --- C�C H�M SETTER CHO D LIU M� H�A (48 bytes) ---

    public void setIdCard(byte[] buffer, short offset, short length){
        if (length > ENCRYPTED_SIZE) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        Util.arrayCopy(buffer, offset, idCard, (short) 0, length);
    }
    public byte[] getIdCard(){ return idCard; }

    public void setFullName(byte[] buffer, short offset, short length) {
        if (length > ENCRYPTED_SIZE) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        Util.arrayCopy(buffer, offset, fullName, (short) 0, length);
    }
    public byte[] getFullName() { return fullName; }

    public void setDateOfBirth(byte[] buffer, short offset, short length) {
        if (length > ENCRYPTED_SIZE) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        Util.arrayCopy(buffer, offset, dateofBirth, (short) 0, length);
    }
    public byte[] getDateOfBirth() { return dateofBirth; }

    public void setAddress(byte[] buffer, short offset, short length) {
        if (length > ENCRYPTED_SIZE) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        Util.arrayCopy(buffer, offset, address, (short) 0, length);
    }
    public byte[] getAddress() { return address; }

    public void setPhoneNumber(byte[] buffer, short offset, short length) {
        if (length > ENCRYPTED_SIZE) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        Util.arrayCopy(buffer, offset, phoneNumber, (short) 0, length);
    }
    public byte[] getPhoneNumber() { return phoneNumber; }


    public void setMoney(byte[] buffer, short offset, short length) {
        if (length > ENCRYPTED_SIZE) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        Util.arrayCopy(buffer, offset, money, (short) 0, length);
    }
    public byte[] getMoney() { return money; }

    public void setExpired(byte[] buffer, short offset, short length) {
        if (length > ENCRYPTED_SIZE) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        Util.arrayCopy(buffer, offset, expired, (short) 0, length);
    }
    public byte[] getExpired() { return expired; }

    // --- AVATAR & GATE ---

    public Avatar getAvatar(){
        return avatar;
    }
    
    public boolean getIsInside() {
        return isInside;
    }
    
    public void setIsInside(boolean inside) {
        isInside = inside;
    }
    
    public byte[] getEntryTime() {
        return entryTime;
    }
    
    public void setEntryTime(byte[] buffer, short offset, short length) {
        if (length != 8) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        Util.arrayCopy(buffer, offset, entryTime, (short)0, (short)8);
    }
}