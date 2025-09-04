/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.process.internal.health.memory

import spock.lang.Specification

class CGroupMemoryInfoTest extends Specification {
    private static final long MB_IN_BYTES = 1024 * 1024 * 1024

    def "parses memory from cgroup values"() {
        def snapshot = new CGroupMemoryInfo().getOsSnapshotFromCgroup(mbsToBytesAsString(800), mbsToBytesAsString(1024))

        expect:
        snapshot.physicalMemory.total == mbsToBytes(1024)
        snapshot.physicalMemory.free == mbsToBytes(224)
    }

    def "negative free memory returns zero"() {
        def snapshot = new CGroupMemoryInfo().getOsSnapshotFromCgroup(mbsToBytesAsString(1024), mbsToBytesAsString(512))

        expect:
        snapshot.physicalMemory.total == mbsToBytes(512)
        snapshot.physicalMemory.free == 0
    }

    def "throws unsupported operation exception when non-numeric values are provided"() {
        when:
        new CGroupMemoryInfo().getOsSnapshotFromCgroup("foo", "bar")

        then:
        thrown(UnsupportedOperationException)
    }

    def "throws unsupported operation exception when 'max' is read in total memory for cgroup v2"() {
        when:
        new CGroupMemoryInfo().getOsSnapshotFromCgroup(mbsToBytesAsString(1), "max")

        then:
        thrown(UnsupportedOperationException)
    }

    def "reads cgroup v2 when exist"() {
        given:
        File tmpDir = File.createTempDir("cgmem", "v2")
        File v2Usage = new File(tmpDir, "memory.current")
        File v2Total = new File(tmpDir, "memory.max")
        File noFile = new File(tmpDir, "noFile")
        v2Usage.text = "10"
        v2Total.text = "50"

        when:
        def snapshot = new CGroupMemoryInfo(noFile.absolutePath, noFile.absolutePath, v2Usage.absolutePath, v2Total.absolutePath).getOsSnapshot()

        then:
        snapshot.physicalMemory.total == 50L
        snapshot.physicalMemory.free == 50L - 10L

        cleanup:
        v2Usage.delete()
        v2Total.delete()
        tmpDir.delete()
    }

    def "reads cgroup v1 when only cgroup v1 exist"() {
        given:
        File tmpDir = File.createTempDir("cgmem", "v1")
        File v1Usage = new File(tmpDir, "memory.usage_in_bytes")
        File v1Total = new File(tmpDir, "memory.limit_in_bytes")
        File noFile = new File(tmpDir, "noFile")
        v1Usage.text = "100"
        v1Total.text = "250"

        when:
        def snapshot = new CGroupMemoryInfo(v1Usage.absolutePath, v1Total.absolutePath, noFile.absolutePath, noFile.absolutePath).getOsSnapshot()

        then:
        snapshot.physicalMemory.total == 250L
        snapshot.physicalMemory.free == 250L - 100L

        cleanup:
        v1Usage.delete()
        v1Total.delete()
        tmpDir.delete()
    }

    def "reads cgroup v1 when cgroup v2 files exist under 'unified' subdir (systemd hybrid mode)"() {
        given:
        File tmpDir = File.createTempDir("cgmem", "hybrid")

        File v1Usage = new File(tmpDir, "memory.usage_in_bytes")
        File v1Total = new File(tmpDir, "memory.limit_in_bytes")
        v1Usage.text = "200"
        v1Total.text = "500"

        File unifiedDir = new File(tmpDir, "unified")
        unifiedDir.mkdirs()
        File v2Usage = new File(unifiedDir, "memory.current")
        File v2Total = new File(unifiedDir, "memory.max")
        v2Usage.text = "10"
        v2Total.text = "20"

        File noFile = new File(tmpDir, "noFile")

        when:
        def snapshot = new CGroupMemoryInfo(v1Usage.absolutePath, v1Total.absolutePath, noFile.absolutePath, noFile.absolutePath).getOsSnapshot()

        then:
        snapshot.physicalMemory.total == 500L
        snapshot.physicalMemory.free == 500L - 200L

        cleanup:
        v1Usage.delete()
        v1Total.delete()
        v2Usage.delete()
        v2Total.delete()
        unifiedDir.delete()
        tmpDir.delete()
    }

    long mbsToBytes(int mbs) {
        return mbs * MB_IN_BYTES
    }

    String mbsToBytesAsString(int mbs) {
        return mbsToBytes(mbs) as String
    }
}
