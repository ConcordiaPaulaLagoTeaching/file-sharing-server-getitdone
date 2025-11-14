package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;

import java.io.RandomAccessFile;
import java.io.EOFException;
import java.util.concurrent.locks.ReentrantLock;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private static FileSystemManager instance;
    private final RandomAccessFile disk;

    private static final int BLOCK_SIZE = 128;
    private final FEntry[] inodeTable;
    private final boolean[] freeBlockList;

    private final ReentrantLock globalLock = new ReentrantLock(true);

    public FileSystemManager(String filename, int totalSize) throws Exception {
        if (instance != null) {
            throw new IllegalStateException("FileSystemManager is already initialized.");
        }
        this.inodeTable = new FEntry[MAXFILES];
        this.freeBlockList = new boolean[MAXBLOCKS];
        this.disk = new RandomAccessFile(filename, "rw");
        long minSize = MAXBLOCKS + MAXFILES * 32L;
        if (disk.length() < minSize) {
            System.out.println("Disk missing or too small, initializing new filesystem...");
            disk.setLength(totalSize);
            for (int i = 0; i < MAXBLOCKS; i++) freeBlockList[i] = true;
            for (int i = 0; i < MAXFILES; i++) inodeTable[i] = null;
            saveMetadata();
        } else {
            try {
                System.out.println("Disk exists, loading filesystem metadata...");
                loadMetadata();
            } catch (Exception e) {
                System.out.println("Corrupted or incomplete disk, reinitializing...");
                for (int i = 0; i < MAXBLOCKS; i++) freeBlockList[i] = true;
                for (int i = 0; i < MAXFILES; i++) inodeTable[i] = null;
                saveMetadata();
            }
        }
        instance = this;
    }

    //makes sure the system saves new files
    private void saveMetadata() throws Exception {
        disk.seek(0);
        // free block list
        for (int i = 0; i < MAXBLOCKS; i++) {
            disk.writeBoolean(freeBlockList[i]);
        }
        // inode table
        for (int i = 0; i < MAXFILES; i++) {
            FEntry e = inodeTable[i];
            if (e == null) {
                disk.writeBoolean(false);
                disk.seek(disk.getFilePointer() + 12 + 2 + 2);
            } else {
                disk.writeBoolean(true);
                byte[] nameBytes = new byte[12]; // zero-filled
                byte[] actualName = e.getFilename().getBytes();
                int len = Math.min(actualName.length, 11);
                System.arraycopy(actualName, 0, nameBytes, 0, len);
                disk.write(nameBytes);
                disk.writeShort(e.getFilesize());
                disk.writeShort(e.getFirstBlock());
            }
        }
    }

    //makes sure the system remembers files
    private void loadMetadata() throws Exception {
        disk.seek(0);
        for (int i = 0; i < MAXBLOCKS; i++) {
            freeBlockList[i] = disk.readBoolean();
        }
        for (int i = 0; i < MAXFILES; i++) {
            boolean exists = false;
            try {
                exists = disk.readBoolean();
            } catch (EOFException ignored) { }
            if (!exists) {
                inodeTable[i] = null;
                disk.seek(disk.getFilePointer() + 12 + 2 + 2);
            } else {
                byte[] nameBytes = new byte[12];
                disk.readFully(nameBytes);

                int nameLen = 0;
                for (; nameLen < nameBytes.length; nameLen++) {
                    if (nameBytes[nameLen] == 0) break;
                }
                String name = new String(nameBytes, 0, nameLen);
                short size = disk.readShort();
                short firstBlock = disk.readShort();
                // Safety: enforce max length 11
                if (name.length() > 11) name = name.substring(0, 11);
                inodeTable[i] = new FEntry(name, size, firstBlock);
            }
        }
    }

    private long blockToOffset(int blockIndex) {
        int inodeTableSize = 32 * MAXFILES;
        return inodeTableSize + (long) blockIndex * BLOCK_SIZE;
    }

    private int findInodeIndex(String fileName) {
        for (int i = 0; i < inodeTable.length; i++) {
            FEntry entry = inodeTable[i];
            if (entry != null && entry.getFilename().equals(fileName)) return i;
        }
        return -1;
    }

    //file creation
    public void createFile(String fileName) throws Exception {
        if (fileName == null || fileName.isEmpty())
            throw new IllegalArgumentException("Filename cannot be null or empty.");
        if (fileName.length() > 11)
            throw new IllegalArgumentException("Filename cannot be longer than 11 characters.");

        globalLock.lock();
        try {
            if (findInodeIndex(fileName) != -1)
                throw new IllegalArgumentException("File with the name " + fileName + " already exists.");

            int freeInodeIndex = -1;
            for (int i = 0; i < inodeTable.length; i++) {
                if (inodeTable[i] == null) {
                    freeInodeIndex = i;
                    break;
                }
            }

            if (freeInodeIndex == -1) throw new Exception("Maximum number of files reached.");

            short firstFreeBlock = -1;
            for (short i = 0; i < freeBlockList.length; i++) {
                if (freeBlockList[i]) {
                    firstFreeBlock = i;
                    freeBlockList[i] = false;
                    break;
                }
            }
            if (firstFreeBlock == -1) throw new Exception("No free blocks available.");

            inodeTable[freeInodeIndex] = new FEntry(fileName, (short) 0, firstFreeBlock);
            saveMetadata();

            System.out.println("File " + fileName + " created successfully (block " + firstFreeBlock + ").");
        } finally {
            globalLock.unlock();
        }
    }
    //writes content into file
    public void writeFile(String fileName, String content) throws Exception {
        int inodeIndex = findInodeIndex(fileName);
        if (inodeIndex == -1) throw new Exception("File does not exist.");

        FEntry f = inodeTable[inodeIndex];
        f.acquireWrite();
        try {
            byte[] data = content.getBytes();
            int blocksNeeded = (int) Math.ceil((double) data.length / BLOCK_SIZE);

            globalLock.lock();
            short allocatedFirst = -1;
            try {
                // free old blocks
                short prev = f.getFirstBlock();
                if (prev >= 0) {
                    for (int i = prev; i < freeBlockList.length; i++) {
                        if (!freeBlockList[i]) freeBlockList[i] = true;
                    }
                }

                int alloc = 0;
                for (short i = 0; i < freeBlockList.length && alloc < blocksNeeded; i++) {
                    if (freeBlockList[i]) {
                        if (allocatedFirst == -1) allocatedFirst = i;
                        freeBlockList[i] = false;
                        alloc++;
                    }
                }
                if (alloc < blocksNeeded) throw new Exception("Not enough free blocks.");

                f.setFilesize((short) data.length);
                f.setFirstBlock(allocatedFirst);
                saveMetadata();
            } finally {
                globalLock.unlock();
            }

            // write file data
            int dataOffset = 0;
            short block = f.getFirstBlock();
            while (dataOffset < data.length && block >= 0 && block < freeBlockList.length) {
                disk.seek(blockToOffset(block));
                int chunkSize = Math.min(BLOCK_SIZE, data.length - dataOffset);
                disk.write(data, dataOffset, chunkSize);
                dataOffset += chunkSize;
                block++;
            }
            //save into disk
            saveMetadata();
        } finally {
            f.releaseWrite();
        }
    }
    //reads content from file
    public String readFile(String fileName) throws Exception {
        int inodeIndex = findInodeIndex(fileName);
        if (inodeIndex == -1) throw new Exception("File does not exist.");

        FEntry f = inodeTable[inodeIndex];
        f.acquireRead();
        try {
            int size = f.getFilesize();
            if (size == 0) return "(empty file)";

            byte[] data = new byte[size];
            int bytesRead = 0;
            short currentBlock = f.getFirstBlock();

            while (bytesRead < size && currentBlock >= 0 && currentBlock < freeBlockList.length) {
                disk.seek(blockToOffset(currentBlock));
                int toRead = Math.min(BLOCK_SIZE, size - bytesRead);
                disk.readFully(data, bytesRead, toRead);
                bytesRead += toRead;
                currentBlock++;
            }

            return new String(data);
        } finally {
            f.releaseRead();
        }
    }
    //deletes the whole file
    public void deleteFile(String fileName) throws Exception {
        int inodeIndex = findInodeIndex(fileName);
        if (inodeIndex == -1) throw new Exception("File does not exist.");

        FEntry f = inodeTable[inodeIndex];
        f.acquireWrite();
        try {
            globalLock.lock();
            try {
                short current = f.getFirstBlock();
                while (current >= 0 && current < freeBlockList.length) {
                    freeBlockList[current] = true;
                    current++;
                }
                inodeTable[inodeIndex] = null;
                saveMetadata();
            } finally {
                globalLock.unlock();
            }
        } finally {
            f.releaseWrite();
        }
    }

    //lists all files
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
}
