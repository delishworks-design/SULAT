@rem Gradle Wrapper for Sulat (Windows)
@rem Uses Gradle 8.13

@setlocal

if not defined JAVA_HOME goto error

"%JAVA_HOME%\bin\java.exe" -jar "%~dp0gradle\wrapper\gradle-wrapper.jar" %*
goto end

:error
echo JAVA_HOME is not set
echo Please set JAVA_HOME to install Java.
exit /b 1

:end
@endlocal
