package DBMS;

import java.io.Serializable;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

public class BitmapIndex implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String columnName;
    private Map<Object, BitSet> bitmaps;
    private int numRecords;
    
    public BitmapIndex(String columnName) {
        if (columnName == null) {
            throw new IllegalArgumentException("Column name cannot be null");
        }
        this.columnName = columnName;
        this.bitmaps = new HashMap<>();
        this.numRecords = 0;
    }
    
    /**
     * Adds a value to the bitmap index for a specific record ID
     * @param value The value to index
     * @param recordId The ID of the record containing this value
     */
    public void addValue(Object value, int recordId) {
        if (value == null) {
            return; // Skip null values
        }
        
        // Ensure we have enough bits
        if (recordId >= numRecords) {
            numRecords = recordId + 1;
            for (BitSet bs : bitmaps.values()) {
                if (bs != null) {
                    bs.set(numRecords - 1, false);
                }
            }
        }
        
        // Initialize all bitmaps to have the same length
        for (BitSet bs : bitmaps.values()) {
            if (bs != null && bs.length() < numRecords) {
                bs.set(bs.length(), numRecords - 1, false);
            }
        }
        
        BitSet bitmap = bitmaps.computeIfAbsent(value, k -> {
            BitSet newBs = new BitSet(numRecords);
            newBs.set(0, numRecords, false); // Initialize all bits to 0
            return newBs;
        });
        bitmap.set(recordId);
    }
    /**
     * Gets the bitmap for records containing a specific value
     * @param value The value to search for
     * @return BitSet where each bit represents whether a record has this value
     */
    public BitSet getBitmap(Object value) {
        BitSet result = bitmaps.get(value);
        if (result == null) {
            return new BitSet(numRecords); // Return empty bitset if value not found
        }
        return result;
    }
    
    /**
     * Gets the bitstream string representation for a specific value
     * @param value The value to get the bitstream for
     * @return String representation of the bitstream (e.g., "10101")
     */
    public String getValueBits(Object value) {
        BitSet bitmap = bitmaps.get(value);
        if (bitmap == null) {
            return createZeroString(numRecords);
        }
        
        StringBuilder sb = new StringBuilder(numRecords);
        for (int i = 0; i < numRecords; i++) {
            sb.append(bitmap.get(i) ? "1" : "0");
        }
        return sb.toString();
    }
    private String createZeroString(int length) {
        char[] zeros = new char[length];
        Arrays.fill(zeros, '0');
        return new String(zeros);
    }
    /**
     * Performs a logical AND operation between bitmaps of two values
     * @param value1 First value
     * @param value2 Second value
     * @return BitSet representing records that have both values
     */
    public BitSet and(Object value1, Object value2) {
        BitSet bs1 = getBitmap(value1);
        BitSet bs2 = getBitmap(value2);
        
        BitSet result = (BitSet) bs1.clone();
        result.and(bs2);
        return result;
    }
    
    /**
     * Gets the number of records indexed
     * @return Total number of records
     */
    public int getNumRecords() {
        return numRecords;
    }
    
    /**
     * Gets the name of the column being indexed
     * @return Column name
     */
    public String getColumnName() {
        return columnName;
    }
    
    /**
     * Updates a record's value in the index
     * @param oldValue The previous value
     * @param newValue The new value
     * @param recordId The ID of the record being updated
     */
    public void updateValue(Object oldValue, Object newValue, int recordId) {
        if (oldValue != null && !oldValue.equals(newValue)) {
            BitSet oldBitmap = bitmaps.get(oldValue);
            if (oldBitmap != null) {
                oldBitmap.clear(recordId);
            }
        }
        
        if (newValue != null) {
            addValue(newValue, recordId);
        }
    }
    
    /**
     * Clears the entire index
     */
    public void clear() {
        bitmaps.clear();
        numRecords = 0;
    }
    
    @Override
    public String toString() {
        // This matches the output format shown in the project description's main method
        StringBuilder sb = new StringBuilder();
        sb.append("BitmapIndex for column: ").append(columnName).append("\n");
        sb.append("Number of records: ").append(numRecords).append("\n");
        
        for (Map.Entry<Object, BitSet> entry : bitmaps.entrySet()) {
            sb.append("Value: ").append(entry.getKey()).append(" - Bitmap: ");
            sb.append(getValueBits(entry.getKey())).append("\n");
        }
        
        return sb.toString();
    }
}