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

import io.github.rosemoe.struct.BlockLinkedList;

import java.util.List;
import java.util.Random;

public class BlockLinkedListTest {

    private static void testPerformance(int times, List<Integer> list) {
        long start = System.nanoTime();
        Random random = new Random();
        for (int i = 0; i < times; i++) {
            int value = random.nextInt();
            list.add(value);
        }
        list.remove(list.size() - 1);
        for (int i = 0; i < times / 2; i++) {
            list.get(random.nextInt(list.size()));
        }
        System.out.println("Object:" + list.getClass().getSimpleName() + " used " + (System.nanoTime() - start) / 1e6 + "ms");
    }

    public static void main(String[] args) {
        BlockLinkedList<Integer> list = new BlockLinkedList<>(10000);
        java.util.List<Integer> list2 = new java.util.ArrayList<>(10000);

        int times = 1000000;
        System.out.println(times + " R/W Test");
        testPerformance(times, list);
        testPerformance(times, list2);

        list.clear();
        list2.clear();
        Random random = new Random();
        for (int i = 0;i < 100000;i++) {
            int value = random.nextInt();
            list.add(value);
            list2.add(value);
        }
        for (int i = 0;i < 50000;i++) {
            int index = random.nextInt(list2.size());
            int a = list.remove(index);
            int b = list2.remove(index);
            if (a != b) {
                System.err.println("Fail - removing");
            }
        }
        for (int i = 0;i < list2.size();i++) {
            if ((int)list.get(i) != (int)list2.get(i)) {
                System.err.println("Fail - reading");
            }
        }

        list2.clear();
    }

}
