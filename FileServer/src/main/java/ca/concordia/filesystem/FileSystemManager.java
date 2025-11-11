package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;

import java.io.RandomAccessFile;
import java.util.concurrent.locks.ReentrantLock;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private static FileSystemManager instance;
    private final RandomAccessFile disk;
    private final ReentrantLock globalLock = new ReentrantLock();

    private static final int BLOCK_SIZE = 128; // Example block size

    private FEntry[] inodeTable; // Array of inodes
    private boolean[] freeBlockList; // Bitmap for free blocks

    public FileSystemManager(String filename, int totalSize) throws Exception {
        // Initialize the file system manager with a file
        if(instance == null) {
            this.inodeTable = new FEntry[MAXFILES];
            this.freeBlockList = new boolean[MAXBLOCKS];
            for (int i = 0; i < MAXBLOCKS; i++) {
                freeBlockList[i] = true; // All blocks are free initially
            }  
            this.disk = new RandomAccessFile(filename, "rw");
            this.disk.setLength(totalSize);
            //TODO Initialize the file system
        } else {
            throw new IllegalStateException("FileSystemManager is already initialized.");
        }

    }

    public void createFile(String fileName) throws Exception {
        globalLock.lock();
        try{
            if (fileName == null || fileName.isEmpty()) {
                throw new IllegalArgumentException("Filename cannot be null or empty.");
            }
            
        if (fileName.length() > 11) {
            throw new IllegalArgumentException("Filename cannot be longer than 11 characters.");
        }

        //check if file already exists
        for (FEntry entry : inodeTable) {
            if (entry != null && entry.getFilename().equals(fileName)) {
                throw new IllegalArgumentException("File with the name " + fileName + "already exists.");
            }
        }

        //find first free inode
        int freeInodeIndex = -1;
        for (int i = 0; i < inodeTable.length; i++) {
            if (inodeTable[i] == null) {
                freeInodeIndex = i;
                break;
            }
        }

        if (freeInodeIndex == -1) {
            throw new Exception("Error: Maximum number of files reached.");
        }

        //find first free block
        short firstFreeBlock = -1;
        for (short i = 0; i < freeBlockList.length; i++) {
            if (freeBlockList[i]) {
                firstFreeBlock = i;
                freeBlockList[i] = false; //marks block as used
                break;
            }
        }
        if (firstFreeBlock == -1) {
            throw new Exception("Error: No free blocks available.");
        }

        //create new FEntry and add to inode table
        inodeTable[freeInodeIndex] = new FEntry(fileName, (short)0, firstFreeBlock);

        System.out.println("File " + fileName + " created successfully (block " + firstFreeBlock + ").");

        } finally {
            globalLock.unlock();
        }
    }


    // TODO: Add readFile, writeFile and other required methods,
}
