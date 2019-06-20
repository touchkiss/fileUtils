package com.touchkiss.utils.fileutils;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * @Author Touchkiss
 * @description: 本地文件复制工具
 * @create: 2019-04-22 10:15
 */
public class LocalCopyUtil {
    private final static String WINDOWS_FILE_SEPERATOR = "\\";
    private final static String UNIX_FILE_SEPERATOR = "/";
    //    需要复制的源文件名，格式为（文件名，是否直接复制）
    private final static Map<String, Boolean> readyToCopyFileList = new HashMap() {{
        put("ZqDomainDescManagerAction.java", false);
        put("importDomainDescStep1.jsp", true);
        put("importDomainDescStep2.jsp", true);
        put("zqDomainDescList.jsp", false);
    }};
    //    需要复制源文件的完整路径，格式为（完整路径，是否直接复制）
    private final static Map<String, Boolean> readToCopyFileListFullPath = new HashMap<>();
    //    Intellij idea安装位置，macOS中idea路径包含空格时，请使用\\ (两个反斜线加一个空格代表一个空格)示例：/Applications/IntelliJ\\ IDEA.app/Contents/MacOS
    private final static String IDEA_LOCATION = "C:\\Program Files\\JetBrains\\IntelliJ IDEA 2018.1.2\\bin\\";


    public static void main(String[] args) {
//        文件来源项目名称
        String fromProject = "from";
        UploadFileUtil.ServerInfo fromProjectInfo = UploadFileUtil.allServer.get(fromProject);
        if (fromProjectInfo == null) {
            System.out.println("未找到该服务器！请检查服务器名称！");
            return;
        }
        readToCopyFileListFullPath.clear();
        readToCopyFileListFullPath.put(fromProjectInfo.getLocalProjectBaseDir() + "src\\resources\\demo.file", true);
        Map<String, String> sourceFileLocationMap = scanAllLocalFiles(fromProjectInfo);
        if (sourceFileLocationMap != null) {
            String[] toProjects = new String[]{
                    "demoDestination"
            };
            for (String toProject : toProjects) {
                UploadFileUtil.ServerInfo destinationProjectServerInfo = UploadFileUtil.allServer.get(toProject);
                if (destinationProjectServerInfo == null) {
                    System.out.println("未找到该服务器！请检查服务器名称！");
                    return;
                }
                boolean finalResult = copyFilesToDestinationProject(fromProjectInfo, sourceFileLocationMap, destinationProjectServerInfo) && copyFilesWithFullPathToDestinationProject(fromProjectInfo, destinationProjectServerInfo);
                if (finalResult) {
                    System.out.println("全部文件复制成功！");
                } else {
                    System.out.println("复制文件出现错误！进程已终止！");
                    return;
                }
            }
        }
    }

    private static boolean copyFilesWithFullPathToDestinationProject(UploadFileUtil.ServerInfo fromProjectInfo, UploadFileUtil.ServerInfo destinationProjectServerInfo) {
        if (readToCopyFileListFullPath.size() > 0) {
            Runtime runtime = Runtime.getRuntime();
            for (Map.Entry<String, Boolean> entry : readToCopyFileListFullPath.entrySet()) {
                Boolean directCopy = entry.getValue();
                String srcFileFullPath = entry.getKey();
                String destinationFileFullPath = destinationProjectServerInfo.getLocalProjectBaseDir() + srcFileFullPath.substring(fromProjectInfo.getLocalProjectBaseDir().length());
                if (directCopy) {
                    if (!copyFile(srcFileFullPath, destinationFileFullPath)) {
                        return false;
                    }
                } else {
                    compareFile(runtime, srcFileFullPath, destinationFileFullPath);
                }
            }
        }
        return true;
    }

    private static void compareFile(Runtime runtime, String srcFileFullPath, String destinationFileFullPath) {
        try {
            File file = new File(destinationFileFullPath);
            if (!file.exists() || !file.isFile()) {
                System.out.println("目标文件：" + destinationFileFullPath + "不存在，无法打开文件比较窗口！");
            } else {
                if (File.separator.equals(WINDOWS_FILE_SEPERATOR)) {
                    runtime.exec("cmd /c c: & cd " + IDEA_LOCATION + " & idea.bat diff " + destinationFileFullPath + " " + srcFileFullPath);
                } else {
                    runtime.exec("cd " + IDEA_LOCATION + "; idea diff " + destinationFileFullPath + " " + srcFileFullPath);
                }
                System.out.println("文件：" + srcFileFullPath.substring(srcFileFullPath.lastIndexOf(File.separator) + 1) + "已为你打开文件比较窗口，请完成后续操作");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean copyFilesToDestinationProject(UploadFileUtil.ServerInfo fromProjectInfo, Map<String, String> sourceFileLocationMap, UploadFileUtil.ServerInfo destinationProjectServerInfo) {
        if (readyToCopyFileList.size() > 0) {
            Runtime runtime = Runtime.getRuntime();
            for (Map.Entry<String, Boolean> entry : readyToCopyFileList.entrySet()) {
                String fileName = entry.getKey();
                Boolean directCopy = entry.getValue();
                String srcFileFullPath = sourceFileLocationMap.get(fileName) + File.separator + fileName;
                String destinationFileFullPath = destinationProjectServerInfo.getLocalProjectBaseDir() + srcFileFullPath.substring(fromProjectInfo.getLocalProjectBaseDir().length());
                if (directCopy) {
                    if (!copyFile(srcFileFullPath, destinationFileFullPath)) {
                        return false;
                    }
                } else {
                    compareFile(runtime, srcFileFullPath, destinationFileFullPath);
                }
            }
        }
        return true;
    }

    private static boolean copyFile(String srcPath, String destPath) {
        boolean hasDestFolder = checkDestFolder(destPath);
        if (!hasDestFolder) {
            return false;
        }
        try {
            FileInputStream fileInputStream = new FileInputStream(srcPath);
            File file = new File(destPath);
            if (!file.exists() || !file.isFile()) {
                file.createNewFile();
            }
            FileOutputStream fileOutputStream = new FileOutputStream(destPath);
            byte[] bytes = new byte[1024 * 8];
            int len = 0;
            while ((len = fileInputStream.read(bytes)) != -1) {
                fileOutputStream.write(bytes, 0, len);
            }
            fileInputStream.close();
            fileOutputStream.close();
            System.out.println("由" + srcPath + "复制到" + destPath + "-------成功");
            return true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean checkDestFolder(String destFilePath) {
        String parentFolderPath = destFilePath.substring(0, destFilePath.lastIndexOf(File.separator));
        File parentFolder = new File(parentFolderPath);
        if (parentFolder.exists() && parentFolder.isDirectory()) {
            return true;
        } else {
            String tmpPath = parentFolderPath.substring(0, parentFolderPath.indexOf(File.separator));
            while (true) {
                File tmpFile = new File(tmpPath);
                if (!tmpFile.exists() || !tmpFile.isDirectory()) {
                    boolean mkdir = tmpFile.mkdir();
                    if (!mkdir) {
                        System.out.println("无法创建文件夹：" + tmpPath);
                        return false;
                    }
                }
                if (tmpPath.equals(parentFolderPath)) {
                    return true;
                }
                String tmp = parentFolderPath.substring(tmpPath.length() + 1);
                if (tmp.indexOf(File.separator) > -1) {
                    tmp = tmp.substring(0, tmp.indexOf(File.separator));
                }
                tmpPath = tmpPath + File.separator + tmp;
            }
        }
    }

    //    检测本地文件情况，判断文件是否存在，是否存在同名未标识文件
    static Map<String, String> scanAllLocalFiles(UploadFileUtil.ServerInfo info) {
        Stack<String> dirSet = new Stack<>();
        dirSet.add(info.getLocalProjectBaseDir() + "WebRoot");
        dirSet.add(info.getLocalProjectBaseDir() + "src");
        Map<String, String> localFileFoundFullPath = new HashMap<>();
        Map<String, Integer> localFileRepeatList = new HashMap<>();

        checkLocalFiles(dirSet, localFileFoundFullPath, localFileRepeatList);
        boolean fullPathFilesAllExist = checkLocalFilesWithFullPath();
        boolean canUpload = true;
        if (localFileRepeatList.size() > 0) {
            System.out.println("发现" + localFileRepeatList.size() + "个本地同名文件，同名文件请使用完整路径添加到readToUploadFileFullPathList，这些文件是：");
            for (Map.Entry<String, Integer> entry : localFileRepeatList.entrySet()) {
                System.out.println("文件名：" + entry.getKey() + "包含" + entry.getValue() + "个同名文件");
            }
            canUpload = false;
        }
        if (localFileFoundFullPath.size() < (readyToCopyFileList.size())) {
            System.out.println("缺少" + (readyToCopyFileList.size() - localFileFoundFullPath.size()) + "个文件：");
            for (String filename : readyToCopyFileList.keySet()) {
                if (!localFileFoundFullPath.containsKey(filename)) {
                    System.out.println(filename);
                }
            }
            canUpload = false;
        }
        if (!canUpload) {
            return null;
        }
        if (fullPathFilesAllExist) {
            System.out.println("已完成本地文件检测，检测通过");
            for (Map.Entry<String, String> entry : localFileFoundFullPath.entrySet()) {
                System.out.println("文件名：" + entry.getKey() + "所在路径：" + entry.getValue());
            }
            return localFileFoundFullPath;
        } else {
            return null;
        }
    }

    //    检查本地文件
    static void checkLocalFiles(Stack<String> dirSet, Map<String, String> localFileFoundFullPath, Map<String, Integer> localFileRepeatList) {
        while (!dirSet.isEmpty()) {
            String folderPath = dirSet.pop();
            File folder = new File(folderPath);
            if (folder.exists() && folder.isDirectory()) {
                String[] list = folder.list();
                if (list != null && list.length > 0) {
                    for (String filename : list) {
                        String pathname = folderPath + File.separator + filename;
                        File tmp = new File(pathname);
                        if (!tmp.exists()) {
                            continue;
                        }
                        if (tmp.isDirectory()) {
                            dirSet.add(tmp.getAbsolutePath() + File.separator);
                        } else {
                            String parentPath = tmp.getParent();
                            if (readyToCopyFileList.containsKey(filename)) {
                                if (localFileFoundFullPath.containsKey(filename)) {
                                    localFileRepeatList.put(filename, 2);
                                    localFileFoundFullPath.remove(filename);
                                } else if (localFileRepeatList.containsKey(filename)) {
                                    Integer fileCount = localFileRepeatList.get(filename);
                                    fileCount++;
                                } else {
                                    localFileFoundFullPath.put(filename, parentPath);
                                }
                            }
                        }
                    }
                }
            } else {
                System.out.println(folder.getAbsolutePath() + "--不是路径");
            }
        }
    }

    private static boolean checkLocalFilesWithFullPath() {
        boolean allExist = true;
        if (readToCopyFileListFullPath != null && readToCopyFileListFullPath.size() > 0) {
            for (String filepath : readToCopyFileListFullPath.keySet()) {
                File file = new File(filepath);
                if (!file.exists()) {
                    allExist = false;
                    System.out.println("本地文件：" + filepath + "不存在！");
                }
            }
        }
        return allExist;
    }
}
