/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.buffer;

import java.util.Arrays;

/**
 * Internal primitive priority queue, used by {@link PoolChunk}.
 * The implementation is based on the binary heap, as described in Algorithms by Sedgewick and Wayne.
 */
final class LongPriorityQueue {
    private long[] array = new long[9];
    private int size;

    public void offer(long handle) {
        size++;
        if (size == array.length) {
            // Grow queue capacity.
            array = Arrays.copyOf(array, 1 + (array.length - 1) * 2);
        }
        array[size] = handle;
        lift();
    }

    public void remove(long value) {
        for (int i = 1; i <= size; i++) {
            if (array[i] == value) {
                if (i == size) {
                    array[i] = 0;
                } else {
                    array[i] = array[size];
                    sink(i);
                }
                size--;
                return;
            }
        }
    }

    public long poll() {
        if (size == 0) {
            return 0;
        }
        long val = array[1];
        array[1] = array[size];
        array[size] = 0;
        size--;
        sink(1);
        return val;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    private void lift() {
        int index = size;
        int parentIndex;
        while (index > 1 && subord(parentIndex = index >> 1, index)) {
            swap(index, parentIndex);
            index = parentIndex;
        }
    }

    private void sink(int index) {
        int child;
        while ((child = index << 1) <= size) {
            if (child < size && subord(child, child + 1)) {
                child++;
            }
            if (!subord(index, child)) {
                break;
            }
            swap(index, child);
            index = child;
        }
    }

    private boolean subord(int a, int b) {
        return array[a] > array[b];
    }

    private void swap(int a, int b) {
        long value = array[a];
        array[a] = array[b];
        array[b] = value;
    }
}
