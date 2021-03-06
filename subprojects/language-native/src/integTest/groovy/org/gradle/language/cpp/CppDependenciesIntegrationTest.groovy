/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.cpp

import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibraries
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.vcs.fixtures.GitRepository

@Requires(TestPrecondition.NOT_WINDOWS)
class CppDependenciesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def app = new CppAppWithLibraries()

    def "can combine C++ builds in a composite"() {
        given:
        settingsFile << """
            include 'app'
            includeBuild 'hello'
            includeBuild 'log'
        """

        writeApp()
        writeHelloLibrary()
        writeLogLibrary()

        when:
        succeeds ":app:installDebug"
        then:
        assertTasksExecutedFor("Debug")
        assertAppHasOutputFor("debug")

        when:
        succeeds ":app:installRelease"
        then:
        assertTasksExecutedFor("Release")
        assertAppHasOutputFor("release")
    }

    def "can depend on C++ libraries from VCS"() {
        given:
        settingsFile << """
            include 'app'

            sourceControl {
                vcsMappings {
                    addRule("org.gradle.cpp VCS rule") { details ->
                        if (details.requested.group == "org.gradle.cpp") {
                            from vcs(GitVersionControlSpec) {
                                url = file(details.requested.module).toURI()
                            }
                        }
                    }
                }
            }
        """

        writeApp()
        writeHelloLibrary()
        writeLogLibrary()

        when:
        succeeds ":app:installDebug"
        then:
        assertTasksExecutedFor("Debug")
        assertAppHasOutputFor("debug")

        when:
        succeeds ":app:installRelease"
        then:
        assertTasksExecutedFor("Release")
        assertAppHasOutputFor("release")
    }

    private void assertTasksExecutedFor(String buildType) {
        assert result.assertTasksExecuted(":hello:compile${buildType}Cpp", ":hello:link${buildType}", ":log:compile${buildType}Cpp", ":log:link${buildType}", ":app:compile${buildType}Cpp", ":app:link${buildType}", ":app:install${buildType}")
    }

    private void assertAppHasOutputFor(String buildType) {
        assert installation("app/build/install/main/${buildType}").exec().out == app.expectedOutput
    }

    private writeApp() {
        app.main.writeToProject(file("app"))
        file("app/build.gradle") << """
            apply plugin: 'cpp-executable'
            group = 'org.gradle.cpp'
            version = '1.0'

            dependencies {
                implementation 'org.gradle.cpp:hello:latest.integration'
            }
        """
    }

    private writeHelloLibrary() {
        def libraryPath = file("hello")
        def libraryRepo = GitRepository.init(libraryPath)
        app.greeterLib.writeToProject(libraryPath)
        libraryPath.file("build.gradle") << """
            apply plugin: 'cpp-library'
            group = 'org.gradle.cpp'
            version = '1.0'
        
            dependencies {
                api 'org.gradle.cpp:log:latest.integration'
            }
        """
        libraryPath.file("settings.gradle").touch()
        libraryRepo.commit("initial commit", libraryRepo.listFiles())
        libraryRepo.close()
    }

    private writeLogLibrary() {
        def logPath = file("log")
        def logRepo = GitRepository.init(logPath)
        app.loggerLib.writeToProject(logPath)
        logPath.file("build.gradle") << """
            apply plugin: 'cpp-library'
            group = 'org.gradle.cpp'
            version = '1.0'
        """
        logPath.file("settings.gradle").touch()
        logRepo.commit("initial commit", logRepo.listFiles())
        logRepo.close()
    }
}
