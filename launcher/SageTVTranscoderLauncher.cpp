#include <filesystem>
#include <iostream>
#include <string>
#include <vector>

#ifdef _WIN32
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#include <shellapi.h>
#else
#include <unistd.h>
#endif

namespace fs = std::filesystem;

static fs::path executable_path() {
#ifdef _WIN32
    std::vector<wchar_t> buf(32768);
    DWORD n = GetModuleFileNameW(nullptr, buf.data(), (DWORD)buf.size());
    return n ? fs::path(std::wstring(buf.data(), n)) : fs::current_path()/"SageTVTranscoder.exe";
#else
    std::vector<char> buf(4096);
    ssize_t n = readlink("/proc/self/exe", buf.data(), buf.size()-1);
    if (n > 0) { buf[(size_t)n] = 0; return fs::path(buf.data()); }
    return fs::current_path()/"SageTVTranscoder";
#endif
}

#ifdef _WIN32
static std::wstring quote_windows(const std::wstring& arg) {
    if (arg.find_first_of(L" \t\"") == std::wstring::npos) return arg;
    std::wstring out=L"\""; unsigned slashes=0;
    for (wchar_t c : arg) {
        if (c==L'\\') { slashes++; continue; }
        if (c==L'\"') { out.append(slashes*2+1,L'\\'); out+=L'\"'; slashes=0; continue; }
        out.append(slashes,L'\\'); slashes=0; out+=c;
    }
    out.append(slashes*2,L'\\'); out+=L'\"'; return out;
}
#endif

int main(int argc, char** argv) {
    const fs::path self = executable_path();
    const fs::path sageHome = self.parent_path();
#ifdef _WIN32
    const fs::path pluginTarget = sageHome/"plugins"/"SageTVFFmpegPlugin"/"runtime"/"ffmpeg_MIM.exe";
    const fs::path stockTarget = sageHome/"ffmpeg.exe";
    const fs::path target = fs::is_regular_file(pluginTarget) ? pluginTarget : stockTarget;
    if (!fs::is_regular_file(target)) { std::cerr << "SageTV FFmpeg Plugin launcher: plugin and stock runtimes missing\n"; return 127; }
    int wargc=0; LPWSTR* wargv=CommandLineToArgvW(GetCommandLineW(), &wargc);
    if (!wargv) return 126;
    std::wstring cmd=quote_windows(target.wstring());
    for (int i=1;i<wargc;i++) { cmd+=L" "; cmd+=quote_windows(wargv[i]); }
    LocalFree(wargv);
    std::vector<wchar_t> mutableCmd(cmd.begin(),cmd.end()); mutableCmd.push_back(0);
    STARTUPINFOW si{}; si.cb=sizeof(si); PROCESS_INFORMATION pi{};
    // SageTV replaces a Fixed-transcoding process by terminating this launcher.
    // A normal parent/child relationship on Windows does not terminate the child,
    // so MIM could otherwise survive until its FFmpeg child eventually noticed a
    // closed output pipe.  Own MIM in a kill-on-close job: forced launcher teardown
    // closes the last job handle and Windows terminates MIM immediately.  MIM owns
    // ffmpeg.real in its own nested job, providing deterministic cascading cleanup.
    HANDLE job=CreateJobObjectW(nullptr,nullptr);
    if(!job) {
        std::cerr << "SageTV FFmpeg Plugin launcher: CreateJobObject failed " << GetLastError() << "\n"; return 126;
    }
    JOBOBJECT_EXTENDED_LIMIT_INFORMATION jobInfo{};
    jobInfo.BasicLimitInformation.LimitFlags=JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
    if(!SetInformationJobObject(job,JobObjectExtendedLimitInformation,&jobInfo,sizeof(jobInfo))) {
        std::cerr << "SageTV FFmpeg Plugin launcher: SetInformationJobObject failed " << GetLastError() << "\n";
        CloseHandle(job); return 126;
    }
    if(!CreateProcessW(target.wstring().c_str(), mutableCmd.data(), nullptr,nullptr,TRUE,CREATE_SUSPENDED,nullptr,sageHome.wstring().c_str(),&si,&pi)) {
        std::cerr << "SageTV FFmpeg Plugin launcher: CreateProcess failed " << GetLastError() << "\n";
        CloseHandle(job); return 126;
    }
    if(!AssignProcessToJobObject(job,pi.hProcess)) {
        std::cerr << "SageTV FFmpeg Plugin launcher: AssignProcessToJobObject failed " << GetLastError() << "\n";
        TerminateProcess(pi.hProcess,126); CloseHandle(pi.hThread); CloseHandle(pi.hProcess); CloseHandle(job); return 126;
    }
    if(ResumeThread(pi.hThread)==(DWORD)-1) {
        std::cerr << "SageTV FFmpeg Plugin launcher: ResumeThread failed " << GetLastError() << "\n";
        TerminateProcess(pi.hProcess,126); CloseHandle(pi.hThread); CloseHandle(pi.hProcess); CloseHandle(job); return 126;
    }
    WaitForSingleObject(pi.hProcess,INFINITE); DWORD rc=1; GetExitCodeProcess(pi.hProcess,&rc);
    CloseHandle(pi.hThread); CloseHandle(pi.hProcess); CloseHandle(job); return (int)rc;
#else
    const fs::path pluginTarget = sageHome/"plugins"/"SageTVFFmpegPlugin"/"runtime"/"ffmpeg_MIM";
    const fs::path stockTarget = sageHome/"ffmpeg";
    const fs::path target = fs::is_regular_file(pluginTarget) ? pluginTarget : stockTarget;
    if (!fs::is_regular_file(target)) { std::cerr << "SageTV FFmpeg Plugin launcher: plugin and stock runtimes missing\n"; return 127; }
    std::vector<std::string> args; args.reserve((size_t)argc); args.push_back(target.string());
    for(int i=1;i<argc;i++) args.push_back(argv[i]);
    std::vector<char*> raw; raw.reserve(args.size()+1); for(auto& s:args) raw.push_back(&s[0]); raw.push_back(nullptr);
    execv(target.c_str(), raw.data());
    perror("SageTV FFmpeg Plugin launcher execv"); return 126;
#endif
}
