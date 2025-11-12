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

    private long blockToOffset(int blockIndex) {
    return (long) blockIndex * BLOCK_SIZE;
    }

    private int findInodeIndex(String fileName) {
    for (int i = 0; i < inodeTable.length; i++) {
        FEntry entry = inodeTable[i];
        if (entry != null && entry.getFilename().equals(fileName)) {
            return i;
        }
    }
    return -1; // File not found
    }

    private void writeFEntryToDisk(int inodeIndex, FEntry entry) throws Exception {
    long offset = inodeIndex * 32L; // Each inode entry takes 32 bytes (example)
    disk.seek(offset);

    // Write filename (fixed 12 bytes — padded or truncated)
    byte[] nameBytes = new byte[12];
    byte[] actualName = entry.getFilename().getBytes();
    int len = Math.min(actualName.length, 11);
    System.arraycopy(actualName, 0, nameBytes, 0, len);
    disk.write(nameBytes);

    // Write filesize (2 bytes)
    disk.writeShort(entry.getFilesize());

    // Write first block (2 bytes)
    disk.writeShort(entry.getFirstBlock());
    }   

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
            instance = this;
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


    public void writeFile(String fileName, String content) throws Exception {
        globalLock.lock();
        try {
            FEntry target = null;
            for (FEntry entry : inodeTable) {
                if (entry != null && entry.getFilename().equals(fileName)) {
                    target = entry;
                    break;
                }
            }
            if (target == null) {
                throw new Exception("Error: File " + fileName+  " not found.");
            }

            byte[] data = content.getBytes();
            int blocksNeeded = (int) Math.ceil((double) data.length / BLOCK_SIZE);

            int freeCount = 0;
            for (boolean free : freeBlockList) {
                if (free) freeCount++;
            }

            if (blocksNeeded > freeCount) {
                throw new Exception("Error: Not enough free blocks available.");
            }

            int dataOffset = 0;
            short lastBlockIndex = -1;
            short firstBlockIndex = target.getFirstBlock();

            disk.seek(blockToOffset(firstBlockIndex));
            int firstChunkSize = Math.min(BLOCK_SIZE, data.length);
            disk.write(data, 0, firstChunkSize);
            dataOffset += firstChunkSize;

            for(int i = 0; i < freeBlockList.length && dataOffset < data.length; i++) {
                if (freeBlockList[i]) {
                    freeBlockList[i] = false; // Mark block as used
                    disk.seek(blockToOffset(i));
                    int chunkSize = Math.min(BLOCK_SIZE, data.length - dataOffset);
                    disk.write(data, dataOffset, chunkSize);
                    dataOffset += chunkSize;
                    lastBlockIndex = (short)i;
                }
            }

            target.setFilesize((short)data.length);
            writeFEntryToDisk(findInodeIndex(fileName), target);

            System.out.println("File " + fileName + " written successfully (" + data.length + " bytes).");

        } finally {
            globalLock.unlock();
        }
    }

    //readFile
    public String readfile(String fileName) throws Exception {
        globalLock.lock();
        try {
            FEntry target = null;
            for (FEntry entry : inodeTable) {
                if (entry != null && entry.getFilename().equals(fileName)) {
                    target = entry;
                    break;
                }
            }
            if (target == null) {
                throw new Exception("Error: File " + fileName + " does not exist.");
            }

            byte[] data = new byte[target.getFilesize()];
            int bytesRead = 0;
            short currentBlock = target.getFirstBlock();
        while (currentBlock != -1 && bytesRead < target.getFilesize()) {
            disk.seek(blockToOffset(currentBlock));
            int bytesToRead = Math.min(BLOCK_SIZE, target.getFilesize() - bytesRead);
            disk.readFully(data, bytesRead, bytesToRead);
            bytesRead += bytesToRead;

            // (improvement: add FNode linkage)
            currentBlock++;
            if (currentBlock >= freeBlockList.length) break;
        }

        return new String(data);

        } finally {
            globalLock.unlock();
        }
    }

   
 //deleteFile
    public void deleteFile(String fileName) throws Exception {
        globalLock.lock();
        try {
            int inodeIndex = findInodeIndex(fileName);
            if (inodeIndex == -1) {
                throw new Exception("Error: File " + fileName + " not found.");
            }

            FEntry target = inodeTable[inodeIndex];

            // Free blocks
            short currentBlock = target.getFirstBlock();
            while (currentBlock != -1) {
                freeBlockList[currentBlock] = true; // Mark block as free

                // (improvement: add FNode linkage)
                currentBlock++;
                if (currentBlock >= freeBlockList.length) break;
            }

            // Remove from inode table
            inodeTable[inodeIndex] = null;

            System.out.println("File " + fileName + " deleted successfully.");

        } finally {
            globalLock.unlock();
        }
    }

//listFiles
    public String[] listFiles() {
        globalLock.lock();
        try {
            return java.util.Arrays.stream(inodeTable)
                    .filter(e -> e != null)
                    .map(FEntry::getFilename)
                    .toArray(String[]::new);
        } finally {
            globalLock.unlock();
        }
    }


    // TODO:  writeFile and other required methods,
}
