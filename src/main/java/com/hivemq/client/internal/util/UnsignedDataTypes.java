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

package com.hivemq.client.internal.util;

/**
 * @author Silvio Giebl
 */
public final class UnsignedDataTypes {

    // 无符号短整形肯定都是正的，要把最高位也用上，所以需要用int来保存
    public static final int UNSIGNED_SHORT_MAX_VALUE = 0xFFFF;
    // 同理，无符号整形需要用long来保存
    public static final long UNSIGNED_INT_MAX_VALUE = 0xFFFF_FFFFL;

    public static boolean isUnsignedShort(final long value) {
        return (value >= 0) && (value <= UNSIGNED_SHORT_MAX_VALUE);
    }

    public static boolean isUnsignedInt(final long value) {
        return (value >= 0) && (value <= UNSIGNED_INT_MAX_VALUE);
    }

    private UnsignedDataTypes() {}
}
