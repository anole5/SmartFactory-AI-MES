@echo off
rem ============================================================
rem SmartFactory-MES build script
rem Switches JAVA_HOME to JDK 17 (Spring Boot 3 requires 17),
rem because the system-wide JAVA_HOME points to JDK 1.8.
rem Usage: run inside backend dir, e.g.  mvn.cmd spring-boot:run
rem NOTE: must use `call` to invoke mvn.cmd, otherwise the
rem       calling script never regains control.
rem ============================================================
set JAVA_HOME=C:\Program Files\Java\jdk-17
set PATH=%JAVA_HOME%\bin;%PATH%
call "D:\install\apache-maven-3.8.4\bin\mvn.cmd" %*
