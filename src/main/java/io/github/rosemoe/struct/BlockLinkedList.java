/*
 * Copyright 2021-2021 Rosemoe
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.github.rosemoe.struct;

import java.util.*;
import java.util.function.Consumer;

/**
 * A powerful block linked list with index cache.
 * It has implemented add(), remove(), set() and get().
 * add(), set() and get() are cached due to better performance.
 *
 * Iterator of this class is rewritten so that we will not find address repeatedly
 * by invocation from universal iterator implemented by java.lang.AbstractList
 *
 * @param <E> Type of elements
 */
@SuppressWarnings("unchecked")
public class BlockLinkedList<E> extends AbstractList<E> {

    private int length;
    private int modCount;
    private final int blockSize;
    private Block head;

    // These are for caching
    private final List<Cache> caches;
    private int foundIndex;
    private Block foundBlock;
    private final static int CACHE_COUNT = 8;
    private final static int CACHE_SWITCH = 30;

    public BlockLinkedList() {
        this(16);
    }

    public BlockLinkedList(int blockSize) {
        this.blockSize = blockSize;
        if (blockSize <= 4) {
            throw new IllegalArgumentException("block size must be bigger than 4");
        }
        length = 0;
        modCount = 0;
        head = new Block();
        caches = new ArrayList<>(CACHE_COUNT + 2);
    }

    /**
     * 0 <=index < length
     */
    private void findBlock1(int index) {
        int distance = index;
        int usedNo = -1;
        Block fromBlock = head;
        for (int i = 0;i < caches.size();i++) {
            Cache c = caches.get(i);
            if (c.indexOfStart < index && (index - c.indexOfStart) < distance) {
                distance = index - c.indexOfStart;
                fromBlock = c.block;
                usedNo = i;
            }
        }
        if (usedNo != -1) {
            Collections.swap(caches, 0, usedNo);
        }
        int crossCount = 0;
        while (distance >= fromBlock.size()) {
            if (fromBlock.next != null) {
                distance -= fromBlock.size();
                fromBlock = fromBlock.next;
            } else {
                break;
            }
            crossCount++;
        }
        if (crossCount >= CACHE_SWITCH) {
            caches.add(cache(index - distance, fromBlock));
        }
        if (caches.size() > CACHE_COUNT) {
            caches.remove(caches.size() - 1);
        }
        foundIndex = distance;
        foundBlock = fromBlock;
    }

    private void invalidateCacheFrom(int index) {
        for (int i = 0;i < caches.size();i++) {
            if (caches.get(i).indexOfStart >= index) {
                caches.remove(i);
                i--;
            }
        }
    }

    @Override
    public void add(int index, E element) {
        if (index < 0 || index > size()) {
            throw new ArrayIndexOutOfBoundsException("index = " + index + ", length = " + size());
        }
        findBlock1(index);
        invalidateCacheFrom(index);
        // Find the block
        Block block = foundBlock;
        index = foundIndex;
        while (index > block.size()) {
            if (block.next == null) {
                // No next block available
                // Add element to this block directly and separate later
                break;
            } else {
                // Go to next block
                index -= block.size();
                block = block.next;
            }
        }
        // Add
        block.add(index, element);
        length++;
        // Separate if required
        if (block.size() > blockSize) {
            block.separate();
        }
        modCount++;
    }

    @Override
    public E remove(int index) {
        if (index < 0 || index >= size()) {
            throw new ArrayIndexOutOfBoundsException("index = " + index + ", length = " + size());
        }
        int backup = index;
        // Find the block
        Block previous = null;
        Block block = head;
        while (index >= block.size()) {
            // Go to next block
            index -= block.size();
            previous = block;
            block = block.next;
        }
        // Remove
        Object removedValue = block.remove(index);
        invalidateCacheFrom(backup - index);
        // Delete blank block
        if (block.size() == 0 && previous != null) {
            previous.next = block.next;
        } else if (block.size() < blockSize / 4 && previous != null && previous.size() + block.size() < blockSize / 2) {
            // Merge small pieces
            previous.next = block.next;
            System.arraycopy(block.data, 0, previous.data, previous.size, block.size);
            previous.size += block.size;
        }
        modCount++;
        length--;
        return (E)removedValue;
    }

    @Override
    public E set(int index, E element) {
        if (index < 0 || index >= size()) {
            throw new ArrayIndexOutOfBoundsException("index = " + index + ", length = " + size());
        }
        findBlock1(index);
        return (E) foundBlock.set(foundIndex, element);
    }

    @Override
    public E get(int index) {
        if (index < 0 || index >= size()) {
            throw new ArrayIndexOutOfBoundsException("index = " + index + ", length = " + size());
        }
        findBlock1(index);
        return (E) foundBlock.get(foundIndex);
    }

    @Override
    protected void removeRange(int fromIndex, int toIndex) {
        // Find the block
        Block previous = null;
        Block block = head;
        while (fromIndex >= block.size()) {
            // Go to next block
            fromIndex -= block.size();
            toIndex -= block.size();
            previous = block;
            block = block.next;
        }
        int deleteLength = toIndex - fromIndex;
        int begin = fromIndex;
        while (deleteLength > 0) {
            if (begin == 0 && deleteLength >= block.size()) {
                // Covers whole region
                if (previous != null) {
                    previous.next = block.next;
                }
                deleteLength -= block.size();
                block.size = 0;
                block = block.next;
                continue;
            }
            int end = Math.min(block.size(), begin + deleteLength);
            block.remove(begin, end);
            deleteLength -= (end - begin);
            previous = block;
            block = block.next;
        }
        length -= (toIndex - fromIndex);
    }

    @Override
    public void clear() {
        head = new Block();
        length = 0;
    }

    @Override
    public int size() {
        return length;
    }

    @Override
    public Iterator<E> iterator() {
        return iterator(0);
    }

    public Iterator<E> iterator(int startIndex) {
        if (startIndex < 0 || startIndex > size()) {
            throw new IndexOutOfBoundsException("index = " + startIndex + ", length = " + size());
        }
        return new BlockIterator(startIndex);
    }

    private class Block {

        public int size() {
            return size;
        }

        private int size;
        private final Object[] data;

        public Block() {
            data = new Object[blockSize + 5];
            size = 0;
        }

        public void add(int index, Object element) {
            // Shift after
            System.arraycopy(data, index, data, index + 1, size - index);
            // Add
            data[index] = element;
            size++;
        }

        public Object set(int index, Object element) {
            Object old = data[index];
            data[index] = element;
            return old;
        }

        public Object get(int index) {
            return data[index];
        }

        public Object remove(int index) {
            Object oldValue = data[index];
            System.arraycopy(data, index + 1, data, index, size - index - 1);
            size--;
            return oldValue;
        }

        public void remove(int start, int end) {
            System.arraycopy(data, end, data, start, size - end);
            size -= (end - start);
        }

        public void separate() {
            Block oldNext = this.next;
            Block newNext = new Block();
            final int divPoint = blockSize / 2;
            System.arraycopy(this.data, divPoint, newNext.data, 0, this.size - divPoint);
            newNext.size = this.size - divPoint;
            this.size = divPoint;
            this.next = newNext;
            newNext.next = oldNext;
        }

        private Block next;

    }

    private class Cache {
        public Block block;
        public int indexOfStart;
    }

    private Cache cache(int index, Block block) {
        Cache c = new Cache();
        c.indexOfStart = index;
        c.block = block;
        return c;
    }

    private class BlockIterator implements Iterator<E> {

        private int localModCount;
        private Block block;
        private int index;
        private int indexInBlock;
        private boolean removeAvailable;

        BlockIterator(int startIndex) {
            localModCount = modCount;
            index = startIndex - 1;
            block = head;
            while (startIndex >= block.size()) {
                startIndex -= block.size();
                block = block.next;
            }
            indexInBlock = startIndex - 1;
            removeAvailable = false;
        }

        private void checkConcurrentMod() {
            if (localModCount != modCount) {
                throw new ConcurrentModificationException();
            }
        }

        @Override
        public boolean hasNext() {
            return index + 1 < size();
        }

        @Override
        public E next() {
            checkConcurrentMod();
            if (hasNext()) {
                while (indexInBlock + 1 >= block.size()) {
                    indexInBlock = -1;
                    block = block.next;
                }
                Object value = block.get(++indexInBlock);
                index++;
                removeAvailable = true;
                return (E) value;
            } else {
                throw new NoSuchElementException();
            }
        }

        @Override
        public void remove() {
            checkConcurrentMod();
            if (!removeAvailable) {
                throw new IllegalStateException("next() has not been called");
            }
            block.remove(indexInBlock);
            modCount++;
            localModCount++;
            indexInBlock--;
            removeAvailable = false;
        }

        @Override
        public void forEachRemaining(Consumer<? super E> action) {
            while (hasNext()) {
                action.accept(next());
            }
        }

    }

}
