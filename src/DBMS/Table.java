package DBMS;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.File;

public class Table implements Serializable
{
	private String name;
	private String[] columnsNames;
	private int pageCount;
	private int recordsCount;
	private ArrayList<String> trace;
	private ArrayList<String> insertTrace;
    private ArrayList<String> queryTrace;
	
	public Table(String name, String[] columnsNames) 
	{
		super();
		this.name = name;
		this.columnsNames = columnsNames;
		this.trace = new ArrayList<String>();
		this.trace.add("Table created name:" + name + ", columnsNames:"
				+ Arrays.toString(columnsNames));
		this.insertTrace = new ArrayList<>();
		this.queryTrace = new ArrayList<>();
	}


	@Override
	public String toString() 
	{
		return "Table [name=" + name + ", columnsNames="
				+ Arrays.toString(columnsNames) + ", pageCount=" + pageCount
				+ ", recordsCount=" + recordsCount + "]";
	}
	public void addInsertTrace(String message) {
        this.trace.add(message);  // Add to main trace list
        this.insertTrace.add(message);
    }
	public void addQueryTrace(String message) {
        this.trace.add(message);  // Add to main trace list
        this.queryTrace.add(message);
    }

	
	public void insert(String []record)
	{
		long startTime = System.currentTimeMillis();
		int pageNum = pageCount - 1;
		Page current = FileManager.loadTablePage(this.name, pageNum);
		if(current == null || !current.insert(record))
		{
			current = new Page();
			current.insert(record);
			pageNum = pageCount; // new page will be at current pageCount
			pageCount++;
		}
		FileManager.storeTablePage(this.name, pageNum, current);
		recordsCount++;
		long stopTime = System.currentTimeMillis();
		this.trace.add(String.format("Inserted: [%s], at page number:%d, execution time (mil):%d",
			String.join(", ", record), pageNum, (stopTime - startTime)));
	}
	public void addInsertTrace(String[] record, int pageNumber, long executionTime) {
        String traceMsg = String.format(
            "Inserted: [%s], at page number:%d, execution time (mil):%d",
            String.join(", ", record),
            pageNumber,
            executionTime
        );
        this.trace.add(traceMsg);
    }

    public void addIndexTrace(String columnName, long executionTime) {
        String traceMsg = String.format(
            "Index created for column: %s, execution time (mil):%d",
            columnName,
            executionTime
        );
        this.trace.add(traceMsg);
    }
	
	public String[] fixCond(String[] cols, String[] vals)
	{
		String[] res = new String[columnsNames.length];
		for(int i=0;i<res.length;i++)
		{
			for(int j=0;j<cols.length;j++)
			{
				if(columnsNames[i].equals(cols[j]))
				{
					res[i]=vals[j];
				}
			}
		}
		return res;
	}
	
	public ArrayList<String []> select(String[] cols, String[] vals)
	{
		String[] cond = fixCond(cols, vals);
		String tracer ="Select condition:"+Arrays.toString(cols)+"->"+Arrays.toString(vals);
		ArrayList<ArrayList<Integer>> pagesResCount = new ArrayList<ArrayList<Integer>>();
		ArrayList<String []> res = new ArrayList<String []>();
		long startTime = System.currentTimeMillis();
		for(int i=0;i<pageCount;i++)
		{
			Page p = FileManager.loadTablePage(this.name, i);
			if (p != null) {
				ArrayList<String []> pRes = p.select(cond);
				if(pRes.size()>0)
				{
					ArrayList<Integer> pr = new ArrayList<Integer>();
					pr.add(i);
					pr.add(pRes.size());
					pagesResCount.add(pr);
					res.addAll(pRes);
				}
			}
		}
		long stopTime = System.currentTimeMillis();
		tracer +=", Records per page:" + pagesResCount+", records:"+res.size()
				+", execution time (mil):"+(stopTime - startTime);
		this.trace.add(tracer);
		return res;
	}
	
	public ArrayList<String []> select(int pageNumber, int recordNumber)
	{
		String tracer ="Select pointer page:"+pageNumber+", record:"+recordNumber;
		ArrayList<String []> res = new ArrayList<String []>();
		long startTime = System.currentTimeMillis();
		Page p = FileManager.loadTablePage(this.name, pageNumber);
		ArrayList<String []> pRes = p.select(recordNumber);
		if(pRes.size()>0)
		{
			res.addAll(pRes);
		}
		long stopTime = System.currentTimeMillis();
		tracer+=", total output count:"+res.size()
				+", execution time (mil):"+(stopTime - startTime);
		this.trace.add(tracer);
		return res;
	}
	
	
	public ArrayList<String []> select()
	{
		ArrayList<String []> res = new ArrayList<String []>();
		long startTime = System.currentTimeMillis();
		for(int i=0;i<pageCount;i++)
		{
			Page p = FileManager.loadTablePage(this.name, i);
			res.addAll(p.select());
		}
		long stopTime = System.currentTimeMillis();
		this.trace.add("Select all pages:" + pageCount+", records:"+recordsCount
				+", execution time (mil):"+(stopTime - startTime));
		return res;
	}
	
	
	public String getName() {
        return this.name;
    }

    public String[] getColumnsNames() {
        return Arrays.copyOf(this.columnsNames, this.columnsNames.length); // Return a copy for immutability
    }

    public int getPageCount() {
        return this.pageCount;
    }

    public int getRecordsCount() {
        return this.recordsCount;
    }

    public ArrayList<String> getTrace() {
        return this.trace;  // Return the actual list, not a copy
    }

    // You might also want these utility getters:
    public int getColumnIndex(String columnName) {
        return Arrays.asList(this.columnsNames).indexOf(columnName);
    }

    public boolean hasColumn(String columnName) {
        return Arrays.asList(this.columnsNames).contains(columnName);
    }
	
	
	
	public String getFullTrace() {
		StringBuilder sb = new StringBuilder();
		
		// Process traces in specific order
		ArrayList<String> tableCreated = new ArrayList<>();
		ArrayList<String> inserts = new ArrayList<>();
		ArrayList<String> indexCreated = new ArrayList<>();
		ArrayList<String> selectIndex = new ArrayList<>();
		ArrayList<String> validation = new ArrayList<>();
		ArrayList<String> recovery = new ArrayList<>();
		
		// Categorize all traces
		for (String trace : this.trace) {
			if (trace.contains("Table created")) {
				tableCreated.add(trace);
			} else if (trace.contains("Inserted")) {
				inserts.add(trace);
			} else if (trace.contains("Index created")) {
				indexCreated.add(trace);
			} else if (trace.contains("Select index")) {
				// Only add if we haven't seen this exact message before
				if (!selectIndex.contains(trace)) {
					selectIndex.add(trace);
				}
			} else if (trace.contains("Validating records")) {
				validation.add(trace);
			} else if (trace.contains("Recovering")) {
				recovery.add(trace);
			}
		}
		
		// Sort inserts by page number
		inserts.sort((a, b) -> {
			int pageA = Integer.parseInt(a.split("page number:")[1].split(",")[0].trim());
			int pageB = Integer.parseInt(b.split("page number:")[1].split(",")[0].trim());
			return Integer.compare(pageA, pageB);
		});
		
		// Add traces in specific order
		// 1. Table creation
		for (String trace : tableCreated) {
			sb.append(trace).append("\n");
		}
		
		// 2. All inserts
		for (String trace : inserts) {
			sb.append(trace).append("\n");
		}
		
		// 3. Validation messages
		for (String trace : validation) {
			sb.append(trace).append("\n");
		}
		
		// 4. Recovery messages
		for (String trace : recovery) {
			sb.append(trace).append("\n");
		}
		
		// 5. Index creation messages
		for (String trace : indexCreated) {
			sb.append(trace).append("\n");
		}
		
		// 6. Select index messages
		for (String trace : selectIndex) {
			sb.append(trace).append("\n");
		}
		
		// Add summary with correct formatting
		sb.append("Pages Count: ").append(pageCount)
		  .append(", Records Count: ").append(recordsCount);
		
		// Add indexed columns properly
		ArrayList<String> indexedCols = new ArrayList<>();
		File tableDir = new File(FileManager.directory, this.name);
		File[] indexFiles = tableDir.listFiles((dir, name) -> 
			name.endsWith(".db") && !name.equals(this.name + ".db") && 
			!name.matches("\\d+\\.db")); // Exclude page files
		
		if (indexFiles != null && indexFiles.length > 0) {
			sb.append(", Indexed Columns: [");
			for (int i = 0; i < indexFiles.length; i++) {
				if (i > 0) sb.append(", ");
				sb.append(indexFiles[i].getName().replace(".db", ""));
			}
			sb.append("]");
		} else {
			sb.append(", Indexed Columns: []");
		}
		return sb.toString();
	}

	public String getLastTrace() {
        if (!queryTrace.isEmpty()) {
            return queryTrace.get(queryTrace.size() - 1);
        }
        if (!insertTrace.isEmpty()) {
            return insertTrace.get(insertTrace.size() - 1);
        }
        if (!trace.isEmpty()) {
            return trace.get(trace.size() - 1);
        }
        return ""; // Return empty string if no traces exist
    }

}
