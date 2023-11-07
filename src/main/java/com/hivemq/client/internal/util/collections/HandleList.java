/*
 * Copyright 2018-present HiveMQ and the HiveMQ Community
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hivemq.client.internal.util.collections;

import com.hivemq.client.internal.annotations.NotThreadSafe;
import org.jetbrains.annotations.NotNull;

/**
 * @author Silvio Giebl
 */
// HandleList并没有什么特殊的，它依旧是一个双向链表，只不过是在NodeList的基础上增加了存储数据的元素
@NotThreadSafe
public class HandleList<E> extends NodeList<HandleList.Handle<E>> {

    // Node本身是个抽象类，只提供了pre和next指针，并没有存储数据的字段，Handle就是在此基础上增加了存储数据的元素
    public static class Handle<E> extends NodeList.Node<Handle<E>> {

        private final @NotNull E element;

        Handle(@NotNull final E element) {
            this.element = element;
        }

        public @NotNull E getElement() {
            return element;
        }
    }

    public @NotNull Handle<E> add(final @NotNull E element) {
        final Handle<E> handle = new Handle<>(element);
        // add操作把handle放在了列表的最后一个位置
        add(handle);
        return handle;
    }
}
