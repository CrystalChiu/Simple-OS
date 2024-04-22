import java.io.*;
import java.lang.Thread;
import java.security.DigestInputStream;
import java.util.Hashtable;

class Disk
    // extends Thread
{
    static final int NUM_SECTORS = 2048;
    static final int DISK_DELAY = 80;
    int id;
    int nextFreeSector;
    StringBuffer[] sectors = new StringBuffer[NUM_SECTORS];
    Disk(int id) {
        this.id = id;
        this.nextFreeSector = 0;

        for (int i = 0; i < NUM_SECTORS; i++) {
            sectors[i] = new StringBuffer();
        }
    }
    void write(int sector, StringBuffer data) throws InterruptedException  // call sleep
    {
        // we only write one line at a time
        sectors[sector].append(data);
        Thread.sleep(DISK_DELAY);
    }
    void read(int sector, StringBuffer data)  // call sleep
    {
        data.append(sectors[sector]);

        try {
            Thread.sleep(DISK_DELAY);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    void printDiskContents() {
        for(int i = 0; i < 50; i++) {
            System.out.println(sectors[i]);
        }
    }
}

class Printer // extends Thread
{
    static final int PRINT_DELAY = 275;
    int id;

    //write data to file: PRINTER<i> where i = index of printer starting from 0
    //write one line at a time

    Printer(int id)
    {
        this.id = id;
    }

    //write to output PRINTER + id
    //but we can only do one buffer at a time
    //we have to check if outfile exists and keep writing to it or we create a new one
    void print(StringBuffer sector) throws IOException {
        String fileName = "PRINTER" + id;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true))) {
            writer.append(sector);
            writer.newLine();
            writer.flush();
        }

        try {
            Thread.sleep(PRINT_DELAY);
        } catch (Exception e) {
            System.out.println("Thread sleep error");
        }
    }
}

class PrintJobThread extends Thread {
    // only allowed one line to reuse for read from disk and print to printer
    String fileName;
    DiskManager diskManage;
    PrinterManager printManage;

    //handles print request
    //get EXCLUSIVE access to a printer (block if they are all busy)
    //repeatedly read sector from disk (AKA one line) then send to printer
    PrintJobThread(DiskManager diskManage, PrinterManager printManage, String fileName) {
        this.diskManage = diskManage;
        this.printManage = printManage;
        this.fileName = fileName;
    }

    @Override
    public void run() {
        try {
            FileInfo fileInfo = diskManage.dirManage.lookup(new StringBuffer(fileName));
            Disk disk = diskManage.getDisk(fileInfo.diskNumber);
            Printer printer = printManage.requestPrinter();
            StringBuffer line = new StringBuffer();

            //System.out.println("Printing in " + fileName + " with Printer " + printer.id + " inside Disk " + disk.id);

            for (int i = 0; i < fileInfo.fileLength; i++) {
                line.setLength(0);
                disk.read(fileInfo.startingSector + i, line);
                printer.print(line);
            }

            printManage.releasePrinter(printer.id);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class FileInfo {
    //contains meta data
    //treat like a struct
    int diskNumber;
    int startingSector;
    int fileLength;

    FileInfo(int diskNumber, int startingSector, int fileLength) {
        this.diskNumber = diskNumber;
        this.startingSector = startingSector;
        this.fileLength = fileLength;
    }

    void printFileInfo() {
        System.out.println("***FileInfo***");
        System.out.println("diskNumber: " + diskNumber + "\nstartingSector: " + startingSector + "\nfileLength: " + fileLength);
        System.out.println("**************");
    }
}

//HOMEWORK 8 TO DO:
class DirectoryManager
{
    //maps key = filename, to val = disk sector (contained in FileInfo)
    private Hashtable<String, FileInfo> fileTable = new Hashtable<String, FileInfo>();
    DirectoryManager() {}

    void enter(StringBuffer fileName, FileInfo file) {
        //add an entry for the file into the table
        fileTable.put(fileName.toString(), file);
    }

    void enter(String fileName, FileInfo file) {
        //add an entry for the file into the table
        fileTable.put(fileName, file);
    }

    FileInfo lookup(StringBuffer fileName) {
        return fileTable.get(fileName.toString());
    }
}

class ResourceManager {
    //gives a specific Thread access rights to a resource (Disk or Printer)
    boolean isFree[];

    ResourceManager(int numberOfItems) {
        isFree = new boolean[numberOfItems];

        for (int i = 0; i < isFree.length; ++i)
            isFree[i] = true;
    }
    synchronized int request() {
        while (true) {
            for (int i = 0; i < isFree.length; ++i)
                if ( isFree[i] ) {
                    isFree[i] = false;
                    return i;

                }

            try {
                this.wait(); // block until someone releases Resource
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        }
    }

    synchronized void release( int index ) {
        isFree[index] = true;
        this.notify(); // let a blocked thread run
    }
}

class DiskManager extends ResourceManager {
    //keeps track of next free sector of disk (so we can save files there)
    //contains DirectoryManager
    DirectoryManager dirManage = new DirectoryManager();
    private Disk[] disks;

    DiskManager(int numberOfItems) {
        super(numberOfItems);
        disks = new Disk[numberOfItems];

        for (int i = 0; i < numberOfItems; i++) {
            disks[i] = new Disk(i);
        }
    }

    //return the first free disk or null if there are none avail
    public synchronized Disk requestDisk() {
        int index = super.request();
        if (index != -1) {
            return disks[index];
        }

        System.out.println("No Disks currently, available");
        return null;
    }

    public synchronized Disk getDisk(int diskId) {
        return disks[diskId];
    }

    public synchronized int getNextFreeSector(int diskId) {
        if(disks[diskId].nextFreeSector < Disk.NUM_SECTORS) {
            return disks[diskId].nextFreeSector;
        } else {
            throw new RuntimeException("No free sector available on disk " + diskId);
        }
    }

    public synchronized void setNextFreeSector(int diskId, int nextFreeSector) {
        disks[diskId].nextFreeSector = nextFreeSector;
    }

    public void printDiskContent(int diskId) {
        disks[diskId].printDiskContents();
    }
}

class PrinterManager extends ResourceManager
{
    private Printer[] printers;

    PrinterManager(int numberOfItems) {
        super(numberOfItems);

        printers = new Printer[numberOfItems];
        for (int i = 0; i < numberOfItems; i++) {
            printers[i] = new Printer(i);
        }
    }

    synchronized Printer requestPrinter() {
        int index = super.request();
        if (index != -1) {
            return printers[index];
        }

        System.out.println("No printers currently, available");
        return null; //none found
    }

    synchronized void releasePrinter(int index) {
        super.release(index);
    }
}

class UserThread extends Thread {

    String inFileName;
    FileInfo fileInfo;
    DiskManager diskManage;
    PrinterManager printManage;
    StringBuffer sharedBuffer;

    UserThread(int id, DiskManager diskManage, PrinterManager printManage) {
        inFileName = "USER" + id;
        this.diskManage = diskManage;
        this.printManage = printManage;
        sharedBuffer = new StringBuffer();
    }

    @Override
    public void run() {
        int dataLineNum = 0;
        try {
            //System.out.println("Disk " + disk.id + "in use");
            Disk disk = null;
            BufferedReader inFile = new BufferedReader(new FileReader(inFileName));
            String fileName = "";
            int curFileLen = 0;
            int startSector = -1;

            for(String line; (line = inFile.readLine()) != null;) {
                String[] parts = line.trim().split("\\s+", 2);
                String cmd = parts[0];
                String arg = parts.length > 1 ? parts[1] : "";

                //define a buffer for each line that we pass in to the disk to write
                //then when we encounter .end --> we grab an available disk and pass in the data?
                    //then within Disk, we create all the sectors O(N) --> N write() calls
                //then when we encounter .print --> we make a new print thread and start it
                    //we grab the Disk we defined (by id number) and then we iterate through the sector array
                        //then we send each arr elem to the printer
                //at the very end, we define a FileInfo object that we will add to DirectoryManager
                    //i think we can make our own FileInfo object to hold all the values and then...
                        //from MainClass we can add that object into the DirectoryManager
                //NOTE: A disk holds MANY files, but they are all stored contiguously
                    //that's why we need to keep track of the starting sector

                switch(cmd) {
                    case ".save":
                        fileName = arg;
                        disk = diskManage.requestDisk();
                        curFileLen = 0;
                        startSector = diskManage.getNextFreeSector(disk.id);
                        break;
                    case ".end":
                        fileInfo = new FileInfo(disk.id, startSector, curFileLen);
                        diskManage.dirManage.enter(fileName, fileInfo);

                        //System.out.println(fileName +  "'s start sector: " + startSector + " in disk " + disk.id);
                        //System.out.println("From: startSector = " + startSector + ", curFileLen = " + curFileLen + " ....");

                        startSector += curFileLen; //the next start should theoretically be where the last one ended + num lines of this one
                        diskManage.setNextFreeSector(disk.id, startSector);
                        //System.out.println("\tDisk " + disk.id + "'s next free sector set as: " + startSector);
                        //System.out.println(fileName +  " releases Disk " + disk.id);
                        diskManage.release(disk.id);
                        break;
                    case ".print":
                        printFile(fileName);
                        break;
                    default:
                        sharedBuffer.setLength(0);
                        sharedBuffer.append(line);
                        //System.out.println("writing to sector: " + (startSector + curFileLen));
                        disk.write(startSector + curFileLen, sharedBuffer);
                        curFileLen++;
                        break;
                }
            }

            inFile.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void printFile(String fileName) throws IOException {
        //System.out.println("Printing file: " + fileName);
        PrintJobThread printJobThread = new PrintJobThread(diskManage, printManage, fileName);
        printJobThread.start();
    }
}


public class MainClass
{
    // command line format will be as follows:
    //$ java -jar 141OS.jar -#ofUsers -#ofDisks -#ofPrinters
    static DiskManager diskManage;
    static PrinterManager printManage;

    public static void main(String args[])
    {
        int numUsers = -1;
        int numDisks = -1;
        int numPrinters = -1;
        //1 sector = 1 line = 1 storage buffer

        for (int i = 0; i < args.length; ++i) {
            //use substring to extract flag value without flag
            switch(i){
                case(0):
                    numUsers = Integer.parseInt(args[i].substring(1));
                    break;
                case(1):
                    numDisks = Integer.parseInt(args[i].substring(1));
                    break;
                case(2):
                    numPrinters = Integer.parseInt(args[i].substring(1));
                    break;
                default:
                    break;
            }
        }
        diskManage = new DiskManager(numDisks);
        printManage = new PrinterManager(numPrinters);

        // start user threads
        UserThread[] userThreads = new UserThread[numUsers];
        for(int i = 0; i < numUsers; i++) {
            userThreads[i] = new UserThread(i, diskManage, printManage);
            userThreads[i].start();
        }
        // join threads
        for(int i = 0; i < numUsers; i++) {
            try {
                System.out.println("joined: " + i);
                userThreads[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        //UserThread newUserThread = new UserThread(0); //id = 0 since we're doing this for USER0 only
        //newUserThread.run(diskManage, printManage); //we'll call start() in HW9
            
        System.out.println("*** Simple OS Simulation ***");
        System.out.println("Users: " + numUsers + ", Disks: " + numDisks + ", Printers: " + numPrinters);

        System.out.println("***Final Disk***");
        for(int i = 0; i < numDisks; i++) {
            System.out.println("----Disk " + i + "----");
            diskManage.printDiskContent(i);
        }
    }
}
