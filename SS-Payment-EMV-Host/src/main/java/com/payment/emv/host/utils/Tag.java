package com.payment.emv.host.utils;

import java.nio.ByteBuffer;

/**
 * Tag object.
 */
public class Tag {
	private static final int MASK_CONSTRUCTED_DATA_OBJECT = 0x20;
	private static final int MASK_SUBSEQUENT_BYTES = 0x1F;
	private static final int MASK_ANOTHER_BYTE = 0x80;		
	
    private final byte[] mBytes;
   
    /**
     * Create new tag from given byte array.
     * 
     * @param bytes tag data.
     */
    public Tag(byte[] bytes) {
        validate(bytes);
        this.mBytes = bytes;
    }
    
	/**
	 * Create new tag from given int value.
	 * 
	 * @param tag tag value.
	 */
	public Tag(int tag) {
		this(encodeTag(tag));		
	}

	// Encode integer tag value to byte array
	private static byte[] encodeTag(int tag) {
		byte b0 = (byte)(tag >> 24);
		byte b1 = (byte)(tag >> 16);
		byte b2 = (byte)(tag >> 8);
		byte b3 = (byte)(tag);
		
		if (b0 != 0) return new byte[] { b0, b1, b2, b3 };
		if (b1 != 0) return new byte[] { b1, b2, b3 };
		if (b2 != 0) return new byte[] { b2, b3 };
		if (b3 != 0) return new byte[] { b3 };
		
		throw new IllegalArgumentException("The argument 'tag' can not be null");
	}
	
	// Validate tag information
    private void validate(byte[] b) {
        if (b == null || b.length == 0) {
            throw new IllegalArgumentException("Tag must be constructed with a non-empty byte array");
        }
        
        if (b.length == 1) {
            if ((b[0] & (byte)MASK_SUBSEQUENT_BYTES) == (byte)MASK_SUBSEQUENT_BYTES) {
                throw new IllegalArgumentException("If first 5 bits are set tag must not be only one byte long");
            }
        } else {
            if ((b[b.length - 1] & (byte)MASK_ANOTHER_BYTE) != (byte) 0x00) {
                throw new IllegalArgumentException("For multibyte tag bit 8 of the final byte must be 0");
            }
            if (b.length > 2) {
                for (int i = 1; i < b.length - 1; i++) {
                    if ((b[i] & (byte)MASK_ANOTHER_BYTE) != (byte)MASK_ANOTHER_BYTE) {
                        throw new IllegalArgumentException("For multibyte tag bit 8 of the internal bytes must be 1");
                    }
                }
            }
        }
    }
    
    /**
     * Returns the tag class.
     * 
     * @return tag class;
     */    
    public TagClass getTagClass() {
    	// Get last 2 bits of tag first byte to determinate class type.
        byte classValue = (byte)(this.mBytes[0] >>> 6 & 0x03);
        
        switch(classValue){
            case (byte)0x00: return TagClass.UNIVERSAL;
            case (byte)0x01: return TagClass.APPLICATION;                
            case (byte)0x02: return TagClass.CONTEXT_SPECIFIC;                
            case (byte)0x03: return TagClass.PRIVATE;          
            // This is not possible at all...
            default: throw new RuntimeException("Tag has invalid class type: " +Integer.toHexString(classValue));
        }                
    }
    
    /**
     * Returns the tag type.
     * 
     * @return tag type;
     */
    public TagType getTagType() {
    	if (isConstructed()) {
    		return TagType.CONSTRUCTED; 
    	} else {
    		return TagType.PRIMITIVE;
    	}
    }       
    
    /**
     * Get tag data.
     * 
     * @return tag data.
     */
    public byte[] getBytes() {
        return mBytes;
    }

    /**
     * Get tag data as int value.
     * 
     * @return tag data.
     */
    public int toIntValue() {
    	int value = 0;
    	
    	for (byte b: mBytes) {
    		value = (value << 8) + (b & 0xff); 
    	}
    	
        return value;
    }
    
    /**
     * Get tag data as hex string.
     * 
     * @return tag data.
     */
    public String toHexValue() {
        return Integer.toHexString(toIntValue()).toUpperCase();
    } 
    
    /**
     * Get whether tag contains constructed data object.
     * 
     * @return true if tag contains constructed data object.
     */
    public boolean isConstructed() {
        return ((mBytes[0] & MASK_CONSTRUCTED_DATA_OBJECT) != 0);
    }
        
    /**
    * Get whether tag contains primitive data object.
    * 
    * @return true if tag contains primitive data object.
    */
    public boolean isPrimitive() {
        return !isConstructed();
    }    
        
    /**
     * Create a new tag from byte buffer.
     * 
     * @param buffer the buffer contains tag data. 
     * @return the tag.
     */
    public static Tag create(ByteBuffer buffer) {
    	byte b = buffer.get();
    	int len = 1;
    	
    	if ((b & MASK_SUBSEQUENT_BYTES) == MASK_SUBSEQUENT_BYTES) {
            do {
                b = buffer.get();
                len++;
            } while ((b & MASK_ANOTHER_BYTE) == MASK_ANOTHER_BYTE);
        }
        
    	byte[] bytes = new byte[len];
    	buffer.position(buffer.position() - len);
    	buffer.get(bytes, 0, bytes.length);
    	
    	return new Tag(bytes);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof Tag)) {
            Tag other = (Tag) obj;
            
            if (mBytes.length != other.mBytes.length) return false;
            
            for (int i = 0; i < mBytes.length; i++) {
            	if (mBytes[i] != other.mBytes[i]) return false;
            }            
            
            return true;
        }
        
        return false;
    }
    
    @Override
    public int hashCode() {
    	int result = 1 + Tag.class.getName().hashCode();
		
		for (byte element : mBytes) {
			result = 31 * result + element;
		}
			
		return result;
    }
    
    @Override
    public String toString() {
    	return "Tag [" + toHexValue() + ", Type=" + getTagType() + ", Class=" + getTagClass() + "]";        
    }
}
