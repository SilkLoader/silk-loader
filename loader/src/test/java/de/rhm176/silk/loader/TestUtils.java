/*
 * Copyright 2025 Silk Loader
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
package de.rhm176.silk.loader;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class TestUtils {
    public static String captureErrAndOut(Runnable runnable) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        ByteArrayOutputStream baosOut = new ByteArrayOutputStream();
        ByteArrayOutputStream baosErr = new ByteArrayOutputStream();

        try (PrintStream psOut = new PrintStream(baosOut);
                PrintStream psErr = new PrintStream(baosErr)) {

            System.setOut(psOut);
            System.setErr(psErr);

            runnable.run();

            psOut.flush();
            psErr.flush();

            return baosOut + baosErr.toString();

        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }
}
