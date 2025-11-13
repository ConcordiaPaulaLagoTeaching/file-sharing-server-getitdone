package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;

import java.io.RandomAccessFile;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FileSystemManager {
    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private static FileSystemManager instance;
    private final RandomAccessFile disk;
    private static final int BLOCK_SIZE = 128; // Example block size

    private final ReentrantReadWriteLock fsLock = new ReentrantReadWriteLock(true);

    private FEntry[] inodeTable; // Array of inodes
    private boolean[] freeBlockList; // Bitmap for free blocks

    private long blockToOffset(int blockIndex) {
        return 32 * 5 + (long) blockIndex * BLOCK_SIZE;
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

        byte[] nameBytes = new byte[12];
        byte[] actualName = entry.getFilename().getBytes();
        int len = Math.min(actualName.length, 11);
        System.arraycopy(actualName, 0, nameBytes, 0, len);
        disk.write(nameBytes);

        disk.writeShort(entry.getFilesize());
        disk.writeShort(entry.getFirstBlock());
    }

    public FileSystemManager(String filename, int totalSize) throws Exception {
        if (instance == null) {
            this.inodeTable = new FEntry[MAXFILES];
            this.freeBlockList = new boolean[MAXBLOCKS];
            for (int i = 0; i < MAXBLOCKS; i++) {
                freeBlockList[i] = true;
            }
            this.disk = new RandomAccessFile(filename, "rw");
            this.disk.setLength(totalSize);
            instance = this;
        } else {
            throw new IllegalStateException("FileSystemManager is already initialized.");
        }
    }

    public void createFile(String fileName) throws Exception {
        fsLock.writeLock().lock();
        try {
            if (fileName == null || fileName.isEmpty())
                throw new IllegalArgumentException("Filename cannot be null or empty.");
            if (fileName.length() > 11)
                throw new IllegalArgumentException("Filename cannot be longer than 11 characters.");

            for (FEntry entry : inodeTable) {
                if (entry != null && entry.getFilename().equals(fileName))
                    throw new IllegalArgumentException("File " + fileName + " already exists.");
            }

            int freeInodeIndex = -1;
            for (int i = 0; i < inodeTable.length; i++) {
                if (inodeTable[i] == null) {
                    freeInodeIndex = i;
                    break;
                }
            }
            if (freeInodeIndex == -1)
                throw new Exception("Error: Maximum number of files reached.");

            short firstFreeBlock = -1;
            for (short i = 0; i < freeBlockList.length; i++) {
                if (freeBlockList[i]) {
                    firstFreeBlock = i;
                    freeBlockList[i] = false;
                    break;
                }
            }
            if (firstFreeBlock == -1)
                throw new Exception("Error: No free blocks available.");

            inodeTable[freeInodeIndex] = new FEntry(fileName, (short) 0, firstFreeBlock);
            System.out.println("File " + fileName + " created successfully (block " + firstFreeBlock + ").");

        } finally {
            fsLock.writeLock().unlock();
        }
    }

    public void writeFile(String fileName, String content) throws Exception {
        fsLock.writeLock().lock();
        try {
            int fileIndex = findInodeIndex(fileName);
            if (fileIndex == -1)
                throw new Exception("ERROR: file " + fileName + " does not exist.");

            FEntry target = inodeTable[fileIndex];
            byte[] data = content.getBytes();
            int blocksNeeded = (int) Math.ceil((double) data.length / BLOCK_SIZE);

            int freeCount = 0;
            for (boolean free : freeBlockList)
                if (free) freeCount++;
            if (blocksNeeded > freeCount)
                throw new Exception("ERROR: file too large — not enough free blocks.");

            byte[] zeroBuffer = new byte[BLOCK_SIZE];
            short firstBlock = target.getFirstBlock();
            if (firstBlock >= 0) {
                for (int i = firstBlock; i < freeBlockList.length; i++) {
                    if (!freeBlockList[i]) {
                        disk.seek(blockToOffset(i));
                        disk.write(zeroBuffer);
                        freeBlockList[i] = true;
                    }
                }
            }

            int dataOffset = 0;
            short firstAllocatedBlock = -1;
            for (short i = 0; i < freeBlockList.length && dataOffset < data.length; i++) {
                if (freeBlockList[i]) {
                    if (firstAllocatedBlock == -1)
                        firstAllocatedBlock = i;
                    freeBlockList[i] = false;
                    disk.seek(blockToOffset(i));
                    int chunkSize = Math.min(BLOCK_SIZE, data.length - dataOffset);
                    disk.write(data, dataOffset, chunkSize);
                    dataOffset += chunkSize;
                }
            }

            target.setFilesize((short) data.length);
            target.setFirstBlock(firstAllocatedBlock);
            writeFEntryToDisk(fileIndex, target);

            System.out.println("File " + fileName + " written successfully (" +
                    data.length + " bytes, " + blocksNeeded + " blocks).");
        } finally {
            fsLock.writeLock().unlock();
        }
    }

    public String readFile(String fileName) throws Exception {
        fsLock.readLock().lock();
        try {
            FEntry target = null;
            for (FEntry entry : inodeTable) {
                if (entry != null && entry.getFilename().equals(fileName)) {
                    target = entry;
                    break;
                }
            }
            if (target == null)
                throw new Exception("Error: File " + fileName + " does not exist.");

            byte[] data = new byte[target.getFilesize()];
            int bytesRead = 0;
            short currentBlock = target.getFirstBlock();
            while (currentBlock != -1 && bytesRead < target.getFilesize()) {
                disk.seek(blockToOffset(currentBlock));
                int bytesToRead = Math.min(BLOCK_SIZE, target.getFilesize() - bytesRead);
                disk.readFully(data, bytesRead, bytesToRead);
                bytesRead += bytesToRead;
                currentBlock++;
                if (currentBlock >= freeBlockList.length) break;
            }
            return new String(data);
        } finally {
            fsLock.readLock().unlock();
        }
    }

    public void deleteFile(String fileName) throws Exception {
        fsLock.writeLock().lock();
        try {
            int inodeIndex = findInodeIndex(fileName);
            if (inodeIndex == -1)
                throw new Exception("ERROR: file " + fileName + " does not exist.");

            FEntry target = inodeTable[inodeIndex];
            byte[] zeroBuffer = new byte[BLOCK_SIZE];

            short currentBlock = target.getFirstBlock();
            while (currentBlock != -1) {
                disk.seek(blockToOffset(currentBlock));
                disk.write(zeroBuffer);
                freeBlockList[currentBlock] = true;
                currentBlock++;
                if (currentBlock >= freeBlockList.length) break;
            }

            inodeTable[inodeIndex] = null;
            System.out.println("File " + fileName + " deleted successfully.");
        } finally {
            fsLock.writeLock().unlock();
        }
    }

    public String[] listFiles() {
        fsLock.readLock().lock();
        try {
            return java.util.Arrays.stream(inodeTable)
                    .filter(e -> e != null)
                    .map(FEntry::getFilename)
                    .toArray(String[]::new);
        } finally {
            fsLock.readLock().unlock();
        }
    }
}
