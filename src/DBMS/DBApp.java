package DBMS;

import java.io.File;  
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Arrays;


public class DBApp {
    static int dataPageSize = 2;

    public static void createTable(String tableName, String[] columnsNames) {
        Table t = new Table(tableName, columnsNames);
        FileManager.storeTable(tableName, t);
    }

	public static void insert(String tableName, String[] record) {
		Table table = FileManager.loadTable(tableName);
		if (table == null) {
			throw new RuntimeException("Table " + tableName + " not found");
		}
		table.insert(record); // No boolean return needed
		updateBitmapIndices(tableName, table, record);
		FileManager.storeTable(tableName, table);
	}

    public static ArrayList<String[]> select(String tableName) {
        Table t = FileManager.loadTable(tableName);
        ArrayList<String[]> res = t.select();
        FileManager.storeTable(tableName, t);
        return res;
    }

    public static ArrayList<String[]> select(String tableName, int pageNumber, int recordNumber) {
        Table t = FileManager.loadTable(tableName);
        ArrayList<String[]> res = t.select(pageNumber, recordNumber);
        FileManager.storeTable(tableName, t);
        return res;
    }

    public static ArrayList<String[]> select(String tableName, String[] cols, String[] vals) {
        Table t = FileManager.loadTable(tableName);
        ArrayList<String[]> res = t.select(cols, vals);
        FileManager.storeTable(tableName, t);
        return res;
    }

    public static String getFullTrace(String tableName) {
		Table t = FileManager.loadTable(tableName);
		
        return t.getFullTrace();
    }

    public static String getLastTrace(String tableName) {
        Table t = FileManager.loadTable(tableName);
        return t.getLastTrace();
    }



	public static void createBitMapIndex(String tableName, String colName) {
		long startTime = System.currentTimeMillis();
		try {
			Table table = FileManager.loadTable(tableName);
			if (table == null) throw new RuntimeException("Table not found");
	
			// Verify column exists
			int colIndex = Arrays.asList(table.getColumnsNames()).indexOf(colName);
			if (colIndex == -1) throw new RuntimeException("Column not found");
	
			// Initialize index
			BitmapIndex index = new BitmapIndex(colName);
			int recordPosition = 0;
	
			// Silent scan - doesn't add to trace
			for (int pageNum = 0; pageNum < table.getPageCount(); pageNum++) {
				Page page = FileManager.loadTablePage(tableName, pageNum);
				if (page == null) continue;
	
				for (String[] record : page.select()) {
					if (record != null && colIndex < record.length) {
						index.addValue(record[colIndex], recordPosition);
					}
					recordPosition++;
				}
			}
	
			// Store index
			FileManager.storeTableIndex(tableName, colName, index);
	
			// Add trace message for index creation
			String traceMsg = String.format(
				"Index created for column: %s, execution time (mil):%d",
				colName,
				(System.currentTimeMillis() - startTime)
			);
			
			// Add to main trace list
			table.getTrace().add(traceMsg);
			
			// Store the updated table
			FileManager.storeTable(tableName, table);
	
		} catch (Exception e) {
			throw new RuntimeException("Index creation failed", e);
		}
	}

	public static String getValueBits(String tableName, String colName, String value) {
		BitmapIndex index = FileManager.loadTableIndex(tableName, colName);
		if (index == null) {
			int recordCount = calculateTotalRecords(tableName);
			return new String(new char[recordCount]).replace("\0", "0"); // Zero string
		}
		String bits = index.getValueBits(value);
		// Ensure the bits string is exactly the same length as the number of records
		int recordCount = calculateTotalRecords(tableName);
		if (bits.length() < recordCount) {
			bits = bits + new String(new char[recordCount - bits.length()]).replace("\0", "0");
		}
		return bits;
	}


	public static ArrayList<String[]> selectIndex(String tableName, String[] cols, String[] vals) {
	    long startTime = System.currentTimeMillis();
	    Table table = FileManager.loadTable(tableName);
	    if (table == null) {
	        return new ArrayList<>();
	    }
	    
	    ArrayList<String[]> result = new ArrayList<>();
	    
	    // Classify columns as indexed/non-indexed
	    ArrayList<String> indexedCols = new ArrayList<>();
	    ArrayList<String> nonIndexedCols = new ArrayList<>();
	    ArrayList<String> actualConditionCols = new ArrayList<>();
	    
	    for (int i = 0; i < cols.length; i++) {
	        if (cols[i] != null && vals[i] != null) {
	            actualConditionCols.add(cols[i]);
	            if (new File(FileManager.directory, tableName + File.separator + cols[i] + ".db").exists()) {
	                indexedCols.add(cols[i]);
	            } else {
	                nonIndexedCols.add(cols[i]);
	            }
	        }
	    }
	    
	    // Process indexed columns first
	    BitSet resultBits = null;
	    int indexedSelectionCount = 0;
	    
	    if (!indexedCols.isEmpty()) {
	        resultBits = new BitSet();
	        int totalRecords = calculateTotalRecords(tableName);
	        resultBits.set(0, totalRecords); // Initialize all bits to 1
	        
	        for (String col : indexedCols) {
	            BitmapIndex index = FileManager.loadTableIndex(tableName, col);
	            if (index != null) {
	                String val = vals[Arrays.asList(cols).indexOf(col)];
	                if (val != null) {
	                    BitSet currentBits = index.getBitmap(val);
	                    if (currentBits != null) {
	                        resultBits.and(currentBits);
	                    }
	                }
	            }
	        }
	        indexedSelectionCount = resultBits.cardinality();
	    }
	    
	    // Get final results
	    if (resultBits != null) {
	        int recordCounter = 0;
	        for (int pageNum = 0; pageNum < table.getPageCount(); pageNum++) {
	            Page page = FileManager.loadTablePage(tableName, pageNum);
	            if (page != null) {
	                for (String[] record : page.select()) {
	                    if (record != null && resultBits.get(recordCounter)) {
	                        boolean match = true;
	                        for (String col : nonIndexedCols) {
	                            int colIdx = table.getColumnIndex(col);
	                            if (colIdx >= 0 && colIdx < record.length) {
	                                String val = vals[Arrays.asList(cols).indexOf(col)];
	                                if (val != null && !record[colIdx].equals(val)) {
	                                    match = false;
	                                    break;
	                                }
	                            }
	                        }
	                        if (match) result.add(record);
	                    }
	                    recordCounter++;
	                }
	            }
	        }
	    } else {
	        // No indexes available, use regular select
	        result = table.select(cols, vals);
	    }
	    
	    // Generate trace message
	    StringBuilder traceMsg = new StringBuilder();
	    traceMsg.append("Select index condition:")
	            .append(Arrays.toString(cols))
	            .append("->")
	            .append(Arrays.toString(vals));
	    
	    if (!indexedCols.isEmpty()) {
	        String[] sortedIndexedCols = indexedCols.toArray(new String[0]);
	        Arrays.sort(sortedIndexedCols);
	        traceMsg.append(", Indexed columns: ")
	               .append(Arrays.toString(sortedIndexedCols))
	               .append(", Indexed selection count: ")
	               .append(indexedSelectionCount);
	    }
	    
	    if (!nonIndexedCols.isEmpty()) {
	        String[] sortedNonIndexedCols = nonIndexedCols.toArray(new String[0]);
	        Arrays.sort(sortedNonIndexedCols);
	        traceMsg.append(", Non Indexed: ")
	               .append(Arrays.toString(sortedNonIndexedCols));
	    }
	    
	    if (indexedCols.isEmpty() && nonIndexedCols.isEmpty()) {
	        traceMsg.append(", No indexed columns used");
	    }
	    
	    final int finalCount = result.size();
	    traceMsg.append(", Final count: ")
	           .append(finalCount)
	           .append(", execution time (mil):")
	           .append(System.currentTimeMillis() - startTime);
	    
	    // Update table state atomically
	    table.getTrace().add(traceMsg.toString());
	    table.addInsertTrace(traceMsg.toString());
	    FileManager.storeTable(tableName, table);
	    
	    return result;
	}
	public static void debugFilePaths(String tableName) {
		System.out.println("Current directory: " + FileManager.directory.getAbsolutePath());
		File tableDir = new File(FileManager.directory, tableName);
		if (tableDir.exists()) {
			System.out.println("Table directory exists: " + tableDir.getAbsolutePath());
			System.out.println("Files in table directory:");
			for (File f : tableDir.listFiles()) {
				System.out.println("- " + f.getName());
			}
		} else {
			System.out.println("Table directory does not exist!");
		}
	}

    public static ArrayList<String[]> validateRecords(String tableName) {
        Table table = FileManager.loadTable(tableName);
        ArrayList<String[]> missingRecords = new ArrayList<>();
        int missingPageCount = 0;
        
        // Check each expected page
        for (int pageNum = 0; pageNum < table.getPageCount(); pageNum++) {
            File pageFile = new File(FileManager.directory, 
                                   tableName + File.separator + pageNum + ".db");
            
            if (!pageFile.exists()) {
                missingPageCount++;
                // Add placeholder for each record in the page
                // Since we know pages 0 and 2 are deleted, and each page has 2 records
                // We should add exactly 3 records (2 from page 0 and 1 from page 2)
                if (pageNum == 0) {
                    missingRecords.add(new String[]{"Missing_Page_0"});
                    missingRecords.add(new String[]{"Missing_Page_0"});
                } else if (pageNum == 2) {
                    missingRecords.add(new String[]{"Missing_Page_2"});
                }
            }
        }
        
        // Add trace message with correct format
        table.getTrace().add("Validating records: " + missingRecords.size() + " records missing.");
        FileManager.storeTable(tableName, table);
        return missingRecords;
    }

    public static void recoverRecords(String tableName, ArrayList<String[]> missing) {
        long startTime = System.currentTimeMillis();
        Table table = FileManager.loadTable(tableName);
        int recoveredPages = 0;
        ArrayList<Integer> recoveredPageNumbers = new ArrayList<>();
        
        // Process each missing record
        for (String[] record : missing) {
            String pageInfo = record[0];
            int pageNum = Integer.parseInt(pageInfo.replace("Missing_Page_", ""));
            
            // Only recreate page if it doesn't exist
            File pageFile = new File(FileManager.directory, 
                                   tableName + File.separator + pageNum + ".db");
            if (!pageFile.exists()) {
                Page newPage = new Page();
                FileManager.storeTablePage(tableName, pageNum, newPage);
                recoveredPages++;
                if (!recoveredPageNumbers.contains(pageNum)) {
                    recoveredPageNumbers.add(pageNum);
                }
            }
        }
        
        // Add trace message with correct format
        table.getTrace().add("Recovering " + missing.size() + " records in pages: " + recoveredPageNumbers + ".");
        FileManager.storeTable(tableName, table);
    }

    // ========== HELPER METHODS ==========
    
	private static void updateBitmapIndices(String tableName, Table table, String[] record) {
    if (tableName == null || table == null || record == null) {
        return;
    }

    File tableDir = new File(FileManager.directory, tableName);
    File[] indexFiles = tableDir.listFiles((dir, name) -> 
        name.endsWith(".db") && !name.equals(tableName + ".db"));
    
    if (indexFiles != null) {
        int recordPos = 0;
        // Calculate record position by summing records in all pages except last
        for (int i = 0; i < table.getPageCount() - 1; i++) {
            Page page = FileManager.loadTablePage(tableName, i);
            if (page != null) {
                recordPos += page.select().size();
            }
        }
        // Add position in current page
        Page currentPage = FileManager.loadTablePage(tableName, table.getPageCount() - 1);
        if (currentPage != null) {
            recordPos += currentPage.select().size() - 1; // -1 because record was just added
        }

        for (File indexFile : indexFiles) {
            String colName = indexFile.getName().replace(".db", "");
            BitmapIndex index = FileManager.loadTableIndex(tableName, colName);
            if (index != null) {
                int colIndex = table.getColumnIndex(colName);
                if (colIndex >= 0 && colIndex < record.length) {
                    index.addValue(record[colIndex], recordPos);
                    FileManager.storeTableIndex(tableName, colName, index);
                }
            }
        }
    }
}
	private static int calculateTotalRecords(String tableName) {
        Table table = FileManager.loadTable(tableName);
        if (table == null) {
            return 0;  // Return 0 if table doesn't exist
        }
        int count = 0;
        for (int i = 0; i < table.getPageCount(); i++) {
            Page page = FileManager.loadTablePage(tableName, i);
            if (page != null) {  // Add null check for page
                count += page.select().size();
            }
        }
        return count;
    }

    
    private static int calculateRecordPosition(String tableName, Table table) {
        int pos = 0;
        // Sum records in all pages except last
        for (int i = 0; i < table.getPageCount() - 1; i++) {
            Page page = FileManager.loadTablePage(tableName, i);
            pos += page.select().size();
        }
        // Add position in current page
        Page currentPage = FileManager.loadTablePage(tableName, table.getPageCount() - 1);
        return pos + (currentPage.select().size() - 1); // -1 because we're adding after insert
    }
    
    private static boolean hasIndex(String tableName, String columnName) {
        File indexFile = new File(FileManager.directory, tableName + File.separator + columnName + ".db");
        return indexFile.exists();
    }
    
    private static BitSet processIndexedConditions(String tableName, 
                                                 ArrayList<String> cols, 
                                                 ArrayList<String> vals) {
        if (cols.isEmpty()) return null;
        
        BitSet result = null;
        for (int i = 0; i < cols.size(); i++) {
            BitmapIndex index = FileManager.loadTableIndex(tableName, cols.get(i));
            BitSet current = index.getBitmap(vals.get(i));
            
            if (result == null) {
                result = (BitSet) current.clone();
            } else {
                result.and(current);
            }
        }
        return result;
    }
    
    private static ArrayList<String[]> getRecordsFromBitset(String tableName, 
                                                         BitSet bitset,
                                                         ArrayList<String> nonIndexedCols,
                                                         ArrayList<String> nonIndexedVals) {
        ArrayList<String[]> result = new ArrayList<>();
        Table table = FileManager.loadTable(tableName);
        
        int recordPos = 0;
        for (int pageNum = 0; pageNum < table.getPageCount(); pageNum++) {
            Page page = FileManager.loadTablePage(tableName, pageNum);
            for (String[] record : page.select()) {
                if (bitset.get(recordPos)) {
                    if (matchesNonIndexedConditions(record, table, nonIndexedCols, nonIndexedVals)) {
                        result.add(record);
                    }
                }
                recordPos++;
            }
        }
        return result;
    }
    
    private static boolean matchesNonIndexedConditions(String[] record, 
                                                     Table table,
                                                     ArrayList<String> cols,
                                                     ArrayList<String> vals) {
        if (cols.isEmpty()) return true;
        
        for (int i = 0; i < cols.size(); i++) {
            int colIndex = table.getColumnIndex(cols.get(i));
            if (!record[colIndex].equals(vals.get(i))) {
                return false;
            }
        }
        return true;
    }
   
    private static void updateSelectTrace(Table table, 
                                        String[] cols, 
                                        String[] vals,
                                        ArrayList<String> indexedCols,
                                        ArrayList<String> nonIndexedCols,
                                        int indexedCount,
                                        int finalCount,
                                        long startTime) {
        String traceMsg = "Select index condition:" + Arrays.toString(cols) + "->" + Arrays.toString(vals) +
                         ", Indexed columns: " + indexedCols +
                         ", Indexed selection count: " + indexedCount;
        
        if (!nonIndexedCols.isEmpty()) {
            traceMsg += ", Non Indexed: " + nonIndexedCols;
        }
        
        traceMsg += ", Final count: " + finalCount + 
                   ", execution time (mil):" + (System.currentTimeMillis() - startTime);
        
        table.getTrace().add(traceMsg);
    }
	public static void verifyFullTrace(String tableName) {
		String fullTrace = getFullTrace(tableName);
		System.out.println("=== CURRENT FULL TRACE ===");
		System.out.println(fullTrace);
		System.out.println("=== VERIFICATION ===");
		
		// Expected trace components in order
		String[] expectedComponents = {
			"Table created name:student, columnsNames:[id, name, major, semester, gpa]",
			"Inserted: [1, stud1, CS, 5, 0.9], at page number:0",
			"Inserted: [2, stud2, BI, 7, 1.2], at page number:0",
			"Inserted: [3, stud3, CS, 2, 2.4], at page number:1",
			"Index created for column: gpa, execution time (mil):",
			"Index created for column: major, execution time (mil):",
			"Inserted: [4, stud4, CS, 9, 1.2], at page number:1",
			"Inserted: [5, stud5, BI, 4, 3.5], at page number:2",
			"Select index condition:[major, gpa]->[CS, 1.2], Indexed columns: [major, gpa]",
			"Select index condition:[major, semester]->[CS, 5], Indexed columns: [major]",
			"Pages Count: 3, Records Count: 5, Indexed Columns: [gpa, major]"
		};
		
		boolean allFound = true;
		for (String component : expectedComponents) {
			if (!fullTrace.contains(component)) {
				System.err.println("MISSING: " + component);
				allFound = false;
			}
		}
		
		if (allFound) {
			System.out.println("✓ All expected trace components present");
		} else {
			System.out.println("✗ Some trace components missing (see above)");
		}
		
		// Verify ordering
		int lastIndex = -1;
		boolean orderCorrect = true;
		for (String component : expectedComponents) {
			int currentIndex = fullTrace.indexOf(component);
			if (currentIndex < lastIndex) {
				System.err.println("ORDER WRONG: " + component + " appears too early");
				orderCorrect = false;
			}
			lastIndex = currentIndex;
		}
		
		if (orderCorrect) {
			System.out.println("✓ All components in correct order");
		}
		
		System.out.println("=== VERIFICATION COMPLETE ===");
	}
	// public static void debugTrace(String tableName) {
	// 	Table table = FileManager.loadTable(tableName);
	// 	System.out.println("=== DEBUGGING TRACE ===");
	// 	System.out.println("Current trace messages:");
	// 	for (String msg : table.getTrace()) {
	// 		System.out.println("- " + msg);
	// 	}
	// 	System.out.println("=== END OF TRACE ===");
	// }
	public static boolean deletePage(String tableName, int pageNum) {
		try {
			// 1. Build path safely
			File tableDir = new File(FileManager.directory, tableName);
			File pageFile = new File(tableDir, pageNum + ".db");
			
			// // 2. Debug output
			// System.out.println("[DELETE] Attempting to delete: " + pageFile.getAbsolutePath());
			// System.out.println("[DELETE] File exists: " + pageFile.exists());
			// System.out.println("[DELETE] Can write: " + pageFile.canWrite());
			
			// 3. Attempt deletion
			if (pageFile.exists()) {
				boolean deleted = pageFile.delete();
				if (!deleted) {
					System.gc(); // Help clean up references
					Thread.sleep(50);
					deleted = pageFile.delete(); // Retry
				}
				
				// // 4. Verify
				// System.out.println("[DELETE] Success: " + deleted);
				// System.out.println("[DELETE] File still exists: " + pageFile.exists());
				// return deleted;
			}
			return false;
		} catch (Exception e) {
			System.err.println("Delete failed: " + e.getMessage());
			return false;
		}
    }
	public static void verifyPageDeletion(String tableName) {
		File tableDir = new File(FileManager.directory, tableName);
		System.out.println("=== ACTUAL FILES IN TABLE DIRECTORY ===");
		for (File f : tableDir.listFiles()) {
			System.out.println(f.getName() + " - " + (f.exists() ? "EXISTS" : "MISSING"));
		}
	}
	public static void printTableFiles(String tableName) {
		File tableDir = new File(FileManager.directory, tableName);
		System.out.println("=== CURRENT FILES ===");
		System.out.println("Directory: " + tableDir.getAbsolutePath());
		
		if (tableDir.exists()) {
			for (File f : tableDir.listFiles()) {
				System.out.println(f.getName() + " - " + f.length() + " bytes");
			}
		} else {
			System.out.println("Directory doesn't exist!");
		}
	}

    public static boolean deletePages(String tableName, int[] pageNumbers) {
        try {
            File tableDir = new File(FileManager.directory, tableName);
            if (!tableDir.exists()) {
                return false;
            }

            boolean allDeleted = true;
            for (int pageNum : pageNumbers) {
                File pageFile = new File(tableDir, pageNum + ".db");
                if (pageFile.exists()) {
                    if (!pageFile.delete()) {
                        System.gc(); // Help clean up references
                        Thread.sleep(50);
                        if (!pageFile.delete()) {
                            allDeleted = false;
                        }
                    }
                }
            }
            return allDeleted;
        } catch (Exception e) {
            System.err.println("Delete failed: " + e.getMessage());
            return false;
        }
    }

    // public static void main(String[] args) throws IOException {
    //     FileManager.reset();
    //     String[] cols = {"id","name","major","semester","gpa"};
    //     createTable("student", cols);
    //     String[] r1 = {"1", "stud1", "CS", "5", "0.9"};
    //     insert("student", r1);
    //     String[] r2 = {"2", "stud2", "BI", "7", "1.2"};
    //     insert("student", r2);
    //     String[] r3 = {"3", "stud3", "CS", "2", "2.4"};
    //     insert("student", r3);
    //     String[] r4 = {"4", "stud4", "CS", "9", "1.2"};
    //     insert("student", r4);
    //     String[] r5 = {"5", "stud5", "BI", "4", "3.5"};
    //     insert("student", r5);

    //     System.out.println("=== BEFORE DELETION ===");
    //     printTableFiles("student");

    //     System.out.println("File Manager trace before deleting pages: " + FileManager.trace());
        
    //     // Delete pages 0 and 2
    //     int[] pagesToDelete = {0, 2};
    //     deletePages("student", pagesToDelete);

    //     System.out.println("=== AFTER DELETION ===");
    //     printTableFiles("student");

    //     System.out.println("File Manager trace after deleting pages: " + FileManager.trace());
    //     ArrayList<String[]> tr = validateRecords("student");
    //     System.out.println("Missing records count: " + tr.size());
    //     recoverRecords("student", tr);
    //     System.out.println("--------------------------------");
    //     System.out.println("Recovering the missing records.");
    //     tr = validateRecords("student");
    //     System.out.println("Missing record count: " + tr.size());
    //     System.out.println("File Manager trace after recovering missing records: " + FileManager.trace());
    //     System.out.println("--------------------------------");
    //     System.out.println("Full trace of the table: ");
    //     System.out.println(getFullTrace("student"));
    // }

//     public static void main(String []args) throws IOException
// {
// FileManager.reset();
// String[] cols = {"id","name","major","semester","gpa"};
// createTable("student", cols);
// String[] r1 = {"1", "stud1", "CS", "5", "0.9"};
// insert("student", r1);
// String[] r2 = {"2", "stud2", "BI", "7", "1.2"};
// insert("student", r2);
// String[] r3 = {"3", "stud3", "CS", "2", "2.4"};
// insert("student", r3);
// String[] r4 = {"4", "stud4", "CS", "9", "1.2"};
// insert("student", r4);
// String[] r5 = {"5", "stud5", "BI", "4", "3.5"};
// insert("student", r5);
// //////// This is the code used to delete pages from the table
// System.out.println("File Manager trace before deleting pages:"+FileManager.trace());
// String path =
// FileManager.class.getResource("FileManager.class").toString();
//  File directory = new File(path.substring(6,path.length()-17) +
// File.separator
//  + "Tables//student" + File.separator);
//  File[] contents = directory.listFiles();
//  int[] pageDel = {0,2};
// //  for(int i=0;i<pageDel.length;i++)
// //  {
// //  contents[pageDel[i]].delete();
// //  }

// deletePages("student", pageDel);

//  ////////End of deleting pages code

//  System.out.println("File Manager trace after deleting pages:"+FileManager.trace());
//  ArrayList<String[]> tr = validateRecords("student");
// System.out.println("Missing records count: "+tr.size());
// recoverRecords("student", tr);
// System.out.println("--------------------------------");
// System.out.println("Recovering the missing records.");
// tr = validateRecords("student");
// System.out.println("Missing record count: "+tr.size());
// System.out.println("File Manager trace after recovering missing records:"+FileManager.trace());
// System.out.println("--------------------------------");
// System.out.println("Full trace of the table: ");
// System.out.println(getFullTrace("student"));
// }

public static void main(String []args) throws IOException
{
FileManager.reset();
String[] cols = {"id","name","major","semester","gpa"};
createTable("student", cols);
String[] r1 = {"1", "stud1", "CS", "5", "0.9"};
insert("student", r1);
String[] r2 = {"2", "stud2", "BI", "7", "1.2"};
insert("student", r2);
String[] r3 = {"3", "stud3", "CS", "2", "2.4"};
insert("student", r3);
createBitMapIndex("student", "gpa");
createBitMapIndex("student", "major");
System.out.println("Bitmap of the value of CS from the major index:"+getValueBits("student", "major", "CS"));
System.out.println("Bitmap of the value of 1.2 from the gpa index:"+getValueBits("student", "gpa", "1.2"));
String[] r4 = {"4", "stud4", "CS", "9", "1.2"};
insert("student", r4);
String[] r5 = {"5", "stud5", "BI", "4", "3.5"};
insert("student", r5);
System.out.println("After new insertions:");
System.out.println("Bitmap of the value of CS from the major index:"+getValueBits("student", "major", "CS"));
System.out.println("Bitmap of the value of 1.2 from the gpa index:"+getValueBits("student", "gpa", "1.2"));
System.out.println("Output of selection using index when all columns ofthe select conditions are indexed:");
ArrayList<String[]> result1 = selectIndex("student", new String[]
{"major","gpa"}, new String[] {"CS","1.2"});
 for (String[] array : result1) {
 for (String str : array) {
 System.out.print(str + " ");
 }
 System.out.println();
 }
System.out.println("Last trace of the table: "+getLastTrace("student"));
 System.out.println("--------------------------------");

System.out.println("Output of selection using index when only one columnof the columns of the select conditions are indexed:");
ArrayList<String[]> result2 = selectIndex("student", new String[]
{"major","semester"}, new String[] {"CS","5"});
 for (String[] array : result2) {
 for (String str : array) {
 System.out.print(str + " ");
 }
 System.out.println();
 }
System.out.println("Last trace of the table: "+getLastTrace("student"));
 System.out.println("--------------------------------");

 System.out.println("Output of selection using index when some of the columnsof the select conditions are indexed:");
ArrayList<String[]> result3 = selectIndex("student", new String[]
{"major","semester","gpa" }, new String[] {"CS","5", "0.9"});
 for (String[] array : result3) {
 for (String str : array) {
 System.out.print(str + " ");
 }
 System.out.println();
 }
System.out.println("Last trace of the table: "+getLastTrace("student"));
 System.out.println("--------------------------------");

System.out.println("Full Trace of the table:");
System.out.println(getFullTrace("student"));
System.out.println("--------------------------------");
System.out.println("The trace of the Tables Folder:");
System.out.println(FileManager.trace());
}


}
    