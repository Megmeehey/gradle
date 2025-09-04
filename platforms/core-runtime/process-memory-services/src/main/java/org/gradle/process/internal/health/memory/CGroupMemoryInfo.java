/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.process.internal.health.memory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

public class CGroupMemoryInfo implements OsMemoryInfo {
    private static final String CG1_MEM_USAGE_FILE = "/sys/fs/cgroup/memory/memory.usage_in_bytes";
    private static final String CG1_MEM_TOTAL_FILE = "/sys/fs/cgroup/memory/memory.limit_in_bytes";
    private static final String CG2_MEM_USAGE_FILE = "/sys/fs/cgroup/memory.current";
    private static final String CG2_MEM_TOTAL_FILE = "/sys/fs/cgroup/memory.max";

    private final String cg1UsageFilePath;
    private final String cg1TotalFilePath;
    private final String cg2UsageFilePath;
    private final String cg2TotalFilePath;

    public CGroupMemoryInfo() {
        this(CG1_MEM_USAGE_FILE, CG1_MEM_TOTAL_FILE, CG2_MEM_USAGE_FILE, CG2_MEM_TOTAL_FILE);
    }

    @VisibleForTesting
    private CGroupMemoryInfo(String cg1UsageFilePath, String cg1TotalFilePath, String cg2UsageFilePath, String cg2TotalFilePath) {
        this.cg1UsageFilePath = cg1UsageFilePath;
        this.cg1TotalFilePath = cg1TotalFilePath;
        this.cg2UsageFilePath = cg2UsageFilePath;
        this.cg2TotalFilePath = cg2TotalFilePath;
    }

    @Override
    public OsMemoryStatus getOsSnapshot() {
        File cg2Usage = new File(cg2UsageFilePath);
        File cg2Total = new File(cg2TotalFilePath);
        if (cg2Usage.exists() && cg2Total.exists()) {
            return getOsSnapshotFromCgroup(
                readStringFromFile(cg2Usage),
                readStringFromFile(cg2Total)
            );
        }
        return getOsSnapshotFromCgroup(
            readStringFromFile(new File(cg1UsageFilePath)),
            readStringFromFile(new File(cg1TotalFilePath))
        );
    }

    private static String readStringFromFile(File file) {
        try {
            return Files.asCharSource(file, Charset.defaultCharset()).readFirstLine();
        } catch (IOException e) {
            throw new UnsupportedOperationException("Unable to read system memory from " + file.getAbsoluteFile(), e);
        }
    }

    @VisibleForTesting
    OsMemoryStatusSnapshot getOsSnapshotFromCgroup(String memUsageString, String memTotalString) {
        long memUsage;
        long memTotal;
        long memAvailable;

        try {
            memUsage = Long.parseLong(memUsageString);
            // cgroup v2 unlimited case where memory.max == "max" is also covered by this
            memTotal = Long.parseLong(memTotalString);
            memAvailable = Math.max(0, memTotal - memUsage);
        } catch (NumberFormatException e) {
            throw new UnsupportedOperationException("Unable to read system memory", e);
        }

        return new OsMemoryStatusSnapshot(memTotal, memAvailable);
    }
}
