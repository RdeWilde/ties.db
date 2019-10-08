/**
 * Copyright © 2017 Ties BV
 *
 * This file is part of Ties.DB project.
 *
 * Ties.DB project is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Ties.DB project is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with Ties.DB project. If not, see <https://www.gnu.org/licenses/lgpl-3.0>.
 */
package com.tiesdb.protocol.v0r0.util;

import static network.tiesdb.util.Hex.DEFAULT_HEX;

import java.util.Arrays;

public final class FormatUtil {

    private FormatUtil() {
    }

    public static String printPartialHex(byte[] bytes) {
        if (null == bytes) {
            return "";
        }
        if (bytes.length <= 64) {
            return DEFAULT_HEX.printHexBinary(bytes);
        } else {
            return DEFAULT_HEX.printHexBinary(Arrays.copyOfRange(bytes, 0, 32)) + "..." //
                    + DEFAULT_HEX.printHexBinary(Arrays.copyOfRange(bytes, bytes.length - 32, bytes.length)) //
                    + "(" + bytes.length + ")";
        }
    }

    public static String printFullHex(byte[] bytes) {
        return null == bytes ? null : DEFAULT_HEX.printHexBinary(bytes);
    }
}
