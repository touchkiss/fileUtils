package com.touchkiss.utils.fileutils;

import org.apache.commons.lang3.StringUtils;
import org.mule.transport.sftp.SftpClient;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @Author Touchkiss
 * @description: 用于将本地文件上传到服务器
 * @date 2018/11/17
 */
public class UploadFileUtil {
    //    远程文件备份后缀，使用该格式每天保留一个备份，可更改其他方式
    private final static DateFormat FILE_BACKUP_EXTENTION = new SimpleDateFormat("yyyyMMdd");
    private final static String WINDOWS_FILE_SEPERATOR = "\\";
    private final static String UNIX_FILE_SEPERATOR = "/";
    private static String TODAY_FILE_BACKUP_EXTENTION = "_" + FILE_BACKUP_EXTENTION.format(new Date());
    public static Map<String, ServerInfo> allServer = new HashMap<>();
    //    需要上传的文件，java文件请写java源文件名，而不是编译后的class文件名，其余文件全部写完整的文件名
    private final static Set<String> readyToUploadFileList = new HashSet() {
        {
            add("Demo.java");
            add("demo.html");
        }
    };
    private final static Set<String> readToUploadFileFullPathList = new HashSet<>();
    private static Set<String> preProcessReadToUploadFileList = new HashSet<>();

    static {
//        添加服务器信息和本地项目信息
        allServer.put("demo", new ServerInfo(
//                远程服务器主机地址
                "123.123.123.123",
//                远程服务器ftp端口号
                22,
//                远程服务器用户名
                "root",
//                远程服务器密码
                "*********",
//                项目在本地的路径
                "D:\\document\\java\\demo",
//                远程服务器上对应的项目class部署路径
                "/opt/apache-tomcat-7.0.73/webapps/ROOT/WEB-INF/classes/",
//                远程服务器上对应的项目资源文件不熟路径
                "/opt/apache-tomcat-7.0.73/webapps/ROOT/WEB-INF/classes/"
        ));
        preProcessReadToUploadFileList();
    }

    //    预处理需要上传的文件名
    static void preProcessReadToUploadFileList() {
        if (readyToUploadFileList != null && readyToUploadFileList.size() > 0) {
            for (String file : readyToUploadFileList) {
                String fileName = file.substring(0, file.lastIndexOf("."));
                String fileExtentionName = file.substring(file.lastIndexOf(".") + 1).toLowerCase();
                switch (fileExtentionName) {
                    case "java":
                        preProcessReadToUploadFileList.add(fileName + ".class");
                        break;
                    default:
                        preProcessReadToUploadFileList.add(file);
                }
            }
        }
    }

    public static void main(String[] args) {
//        待上传的项目列表
        String[] serverList = new String[]{
                "demo"
        };
        for (String serverName : serverList) {
            ServerInfo serverInfo = allServer.get(serverName);
            System.out.println("开始处理" + serverName);
            readToUploadFileFullPathList.clear();
//            若项目中存在重名文件,请将绝对地址添加到这里
            readToUploadFileFullPathList.add(serverInfo.localProjectBaseDir + "WebRoot\\WEB-INF\\classes\\demo.file");
            if (serverInfo == null) {
                System.out.println("未找到该服务器！请检查服务器名称！");
                return;
            }
            Map<String, String> localBuiltFileLocationMap = scanAllLocalFiles(serverInfo);
            if (localBuiltFileLocationMap != null) {
                SftpClient server = getClient(serverInfo);
                boolean finalResult = uploadFilesToRemoteServer(server, serverInfo, localBuiltFileLocationMap) && uploadFilesWithFullPathToRemoteServer(server, serverInfo);
                if (finalResult) {
                    System.out.println("全部文件上传成功！");
                } else {
                    System.out.println("上传文件出现错误！进程已终止！");
                    server.disconnect();
                    return;
                }
                server.disconnect();
            }
        }
    }

    static boolean uploadFilesWithFullPathToRemoteServer(SftpClient server, ServerInfo serverInfo) {
        if (readToUploadFileFullPathList != null && readToUploadFileFullPathList.size() > 0) {
            for (String filepath : readToUploadFileFullPathList) {
                String remoteFilePath = getRemoteFilePath(serverInfo, filepath);
                boolean uploadStatus = uploadFileToRemoteServer(server, filepath, remoteFilePath);
                if (!uploadStatus) {
                    return false;
                }
            }
        }
        return true;
    }

    //    上传文件到服务器
    static boolean uploadFilesToRemoteServer(SftpClient server, ServerInfo
            serverInfo, Map<String, String> localBuiltFileLocationMap) {
        if (preProcessReadToUploadFileList.size() > 0) {
            for (String filename : preProcessReadToUploadFileList) {
                String extentionName = "";
                if (filename.indexOf(".") > -1) {
                    extentionName = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
                }
                String fileParentPath = localBuiltFileLocationMap.get(filename);
                switch (extentionName) {
                    case "class":
//                        编译好的java文件
                        String pureFilename = filename.substring(0, filename.lastIndexOf("."));
                        File parentFolder = new File(fileParentPath);
                        if (parentFolder != null && parentFolder.exists() && parentFolder.isDirectory()) {
                            String[] list = parentFolder.list();
                            if (list != null && list.length > 0) {
                                for (String brotherFile : list) {
                                    if (brotherFile.equals(filename) || (brotherFile.startsWith(pureFilename + "$"))) {
                                        String remoteFilePath = getRemoteFilePath(serverInfo, fileParentPath + File.separator + brotherFile);
                                        if (!StringUtils.isEmpty(remoteFilePath)) {
                                            boolean uploadStatus = uploadFileToRemoteServer(server, fileParentPath + File.separator + brotherFile, remoteFilePath);
                                            if (!uploadStatus) {
                                                return false;
                                            }
                                        } else {
                                            System.out.println("获取远程服务器文件路径错误！");
                                            return false;
                                        }
                                    }
                                }
                            }
                        } else {
                            System.out.println("文件夹：" + fileParentPath + "不存在");
                            return false;
                        }
                        break;
                    default:
                        String fileFullPath = fileParentPath + File.separator + filename;
                        String remoteFilePath = getRemoteFilePath(serverInfo, fileFullPath);
                        if (!StringUtils.isEmpty(remoteFilePath)) {
                            boolean uploadStatus = uploadFileToRemoteServer(server, fileFullPath, remoteFilePath);
                            if (!uploadStatus) {
                                return false;
                            }
                        } else {
                            System.out.println("获取远程服务器文件路径错误！");
                            return false;
                        }
//                            其他文件：html\css\js\jsp等
                        break;
                }
            }
        }
        return true;
    }

    //    计算远程服务器文件路径
    static String getRemoteFilePath(ServerInfo serverInfo, String localFilePath) {
        String remoteFilePath = null;
        String localFileLocation = null;
        if (localFilePath.startsWith(serverInfo.getLocalProjectBaseDir() + "WebRoot" + File.separator + "WEB-INF" + File.separator + "classes")) {
            localFileLocation = serverInfo.getLocalProjectBaseDir() + "WebRoot" + File.separator + "WEB-INF" + File.separator + "classes" + File.separator;
            remoteFilePath = serverInfo.getRemoteClassFilesBaseDir() + localFilePath.substring(localFileLocation.length());
        } else if (localFilePath.startsWith(serverInfo.getLocalProjectBaseDir() + "WebRoot") && localFilePath.indexOf(serverInfo.getLocalProjectBaseDir() + "WebRoot" + File.separator + "WEB-INF") == -1) {
            localFileLocation = serverInfo.getLocalProjectBaseDir() + "WebRoot" + File.separator;
            remoteFilePath = serverInfo.getRemoteResourceFilesBaseDir() + localFilePath.substring(localFileLocation.length());
        } else if (localFilePath.startsWith(serverInfo.getLocalProjectBaseDir() + "target")) {
            localFileLocation = serverInfo.getLocalProjectBaseDir() + "target" + File.separator + "classes" + File.separator;
            remoteFilePath = serverInfo.getRemoteClassFilesBaseDir() + localFilePath.substring(localFileLocation.length());
        }
        if (File.separator.equals(WINDOWS_FILE_SEPERATOR)) {
            remoteFilePath = remoteFilePath.replaceAll("\\\\", UNIX_FILE_SEPERATOR);
        }
        return remoteFilePath;
    }

    //    上传文件到服务器
    static boolean uploadFileToRemoteServer(SftpClient server, String localFile, String remoteFile) {
        boolean remoteServerHasThisFile;
        try {
            long size = server.getSize(remoteFile);
            remoteServerHasThisFile = true;
        } catch (IOException e) {
//            e.printStackTrace();
            remoteServerHasThisFile = false;
        }
        if (!remoteServerHasThisFile) {
            checkRemoteFolder(server, remoteFile);
        } else {
            boolean remoteServerHasBackupThisFileToday;
            try {
                server.getSize(remoteFile + TODAY_FILE_BACKUP_EXTENTION);
                remoteServerHasBackupThisFileToday = true;
            } catch (IOException e) {
//            e.printStackTrace();
                remoteServerHasBackupThisFileToday = false;
            }
            if (!remoteServerHasBackupThisFileToday) {
                try {
                    server.rename(remoteFile, remoteFile + TODAY_FILE_BACKUP_EXTENTION);
                    System.out.println("备份服务器文件：" + remoteFile + "备份后名称：" + remoteFile + TODAY_FILE_BACKUP_EXTENTION);
                } catch (IOException e) {
//                e.printStackTrace();
                    System.out.println("备份服务器文件：" + remoteFile + "失败");
                    return false;
                }
            }
        }
        try {
            System.out.println("准备将本地文件：" + localFile + "复制到:" + remoteFile);
            server.storeFile(localFile, remoteFile);
            System.out.println("成功上传文件：" + remoteFile);
            return true;
        } catch (Exception e) {
//            e.printStackTrace();
            System.out.println("将本地文件：" + localFile + "复制到:" + remoteFile + "失败");
            return false;
        }
    }

    //    检查远程服务器文件夹
    static boolean checkRemoteFolder(SftpClient server, String remoteFile) {
        String parentFolderPath = remoteFile.substring(0, remoteFile.lastIndexOf(UNIX_FILE_SEPERATOR));
        try {
            server.getSize(parentFolderPath);
            return true;
        } catch (IOException e) {
//            e.printStackTrace();
            String tmpPath = parentFolderPath.substring(1);
            tmpPath = UNIX_FILE_SEPERATOR + tmpPath.substring(0, tmpPath.indexOf(UNIX_FILE_SEPERATOR));
            while (true) {
                try {
                    server.getSize(tmpPath);
                } catch (IOException e1) {
//                    e1.printStackTrace();
                    try {
                        server.mkdir(tmpPath);
                    } catch (IOException e2) {
//                        e2.printStackTrace();
                        System.out.println("无法在远程服务器上创建目录：" + tmpPath);
                        return false;
                    }
                }
                if (tmpPath.equals(parentFolderPath)) {
                    return true;
                }
                String tmp = parentFolderPath.substring(tmpPath.length() + 1);
                if (tmp.indexOf(UNIX_FILE_SEPERATOR) > -1) {
                    tmp = tmp.substring(0, tmp.indexOf(UNIX_FILE_SEPERATOR));
                }
                tmpPath = tmpPath + UNIX_FILE_SEPERATOR + tmp;
            }
        }
    }


    //    检测本地文件情况，判断文件是否存在，是否存在同名未标识文件
    static Map<String, String> scanAllLocalFiles(ServerInfo info) {
        Stack<String> dirSet = new Stack<>();
        dirSet.addAll(info.getLocalFileBaseDirList());
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
        if (localFileFoundFullPath.size() < (readyToUploadFileList.size())) {
            System.out.println("缺少" + (readyToUploadFileList.size() - localFileFoundFullPath.size()) + "个文件：");
            for (String filename : preProcessReadToUploadFileList) {
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

    private static boolean checkLocalFilesWithFullPath() {
        boolean allExist = true;
        if (readToUploadFileFullPathList != null && readToUploadFileFullPathList.size() > 0) {
            for (String filepath : readToUploadFileFullPathList) {
                File file = new File(filepath);
                if (!file.exists()) {
                    allExist = false;
                    System.out.println("本地文件：" + filepath + "不存在！");
                }
            }
        }
        return allExist;
    }

    //    检查本地文件
    static void checkLocalFiles
    (Stack<String> dirSet, Map<String, String> localFileFoundFullPath, Map<String, Integer> localFileRepeatList) {
        while (!dirSet.isEmpty()) {
            String folderPath = dirSet.pop();
            File folder = new File(folderPath);
            if (folder.exists() && folder.isDirectory()) {
                String[] list = folder.list();
                if (list != null && list.length > 0) {
                    for (String filename : list) {
                        File tmp = new File(folderPath + File.separator + filename);
                        if (!tmp.exists()) {
                            continue;
                        }
                        if (tmp.isDirectory()) {
                            dirSet.add(tmp.getAbsolutePath() + File.separator);
                        } else {
                            String parentPath = tmp.getParent();
                            if (preProcessReadToUploadFileList.contains(filename)) {
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

    //    取得sftp客户端连接
    static SftpClient getClient(ServerInfo info) {
        try {
            SftpClient client = new SftpClient(info.getHost());
            client.setPort(info.getPort());
            client.login(info.getUsername(), info.getPasscode());
            return client;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    //    基础信息类
    static class ServerInfo {
        //        ip
        private String host;
        //        端口号
        private int port;
        //        用户
        private String username;
        //        密码
        private String passcode;
        //        本地项目文件夹
        private String localProjectBaseDir;
        //        本地文件目录列表
        private List<String> localFileBaseDirList;
        //        远程服务器class文件基准目录（class）
        private String remoteClassFilesBaseDir;
        //        远程服务器其他资源类文件基准目录（jsp、html、css、js、img等）
        private String remoteResourceFilesBaseDir;

        public ServerInfo(String host, int port, String username, String passcode, String localProjectBaseDir, String remoteClassFilesBaseDir, String remoteResourceFilesBaseDir) {
            this.host = host;
            this.port = port;
            this.username = username;
            this.passcode = passcode;
            if (localProjectBaseDir.endsWith(File.separator)) {
                this.localProjectBaseDir = localProjectBaseDir;
            } else {
                this.localProjectBaseDir = localProjectBaseDir + File.separator;
            }
            if (remoteClassFilesBaseDir.endsWith(UNIX_FILE_SEPERATOR)) {
                this.remoteClassFilesBaseDir = remoteClassFilesBaseDir;
            } else {
                this.remoteClassFilesBaseDir = remoteClassFilesBaseDir;
            }
            if (remoteResourceFilesBaseDir.endsWith(UNIX_FILE_SEPERATOR)) {
                this.remoteResourceFilesBaseDir = remoteResourceFilesBaseDir;
            } else {
                this.remoteResourceFilesBaseDir = remoteResourceFilesBaseDir + UNIX_FILE_SEPERATOR;
            }
            this.addLocalFileDir("target" + File.separator + "classes");
            this.addLocalFileDir("WebRoot");
        }

        public void addLocalFileDir(String relativePath) {
            if (this.localFileBaseDirList == null) {
                this.localFileBaseDirList = new ArrayList<>();
            }
            if (!relativePath.startsWith(File.separator)) {
                this.localFileBaseDirList.add(this.localProjectBaseDir + relativePath);
            } else {
                this.localFileBaseDirList.add(this.localProjectBaseDir + relativePath.substring(1));
            }
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPasscode() {
            return passcode;
        }

        public void setPasscode(String passcode) {
            this.passcode = passcode;
        }

        public String getLocalProjectBaseDir() {
            return localProjectBaseDir;
        }

        public void setLocalProjectBaseDir(String localProjectBaseDir) {
            this.localProjectBaseDir = localProjectBaseDir;
        }

        public List<String> getLocalFileBaseDirList() {
            return localFileBaseDirList;
        }

        public void setLocalFileBaseDirList(List<String> localFileBaseDirList) {
            this.localFileBaseDirList = localFileBaseDirList;
        }

        public String getRemoteClassFilesBaseDir() {
            return remoteClassFilesBaseDir;
        }

        public void setRemoteClassFilesBaseDir(String remoteClassFilesBaseDir) {
            this.remoteClassFilesBaseDir = remoteClassFilesBaseDir;
        }

        public String getRemoteResourceFilesBaseDir() {
            return remoteResourceFilesBaseDir;
        }

        public void setRemoteResourceFilesBaseDir(String remoteResourceFilesBaseDir) {
            this.remoteResourceFilesBaseDir = remoteResourceFilesBaseDir;
        }
    }
}