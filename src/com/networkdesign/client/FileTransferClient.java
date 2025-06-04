package com.networkdesign.client;

import com.networkdesign.protocol.FileTransferProtocol;
import com.networkdesign.util.ProtocolUtil;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件传输客户端
 * 处理文件上传和下载
 */
public class FileTransferClient {
    private final String serverAddress;
    private final int serverPort;
    private Socket socket;
    private InputStream in;
    private OutputStream out;

    public FileTransferClient(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }

    /**
     * 上传文件
     */
    public void uploadFile(String filePath, ProgressCallback callback) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("文件不存在: " + filePath);
        }

        try {
            connect();
            
            // 发送文件信息
            String fileInfo = file.getName() + "|" + file.length();
            FileTransferProtocol.Message infoMessage = new FileTransferProtocol.Message(
                FileTransferProtocol.FILE_INFO,
                fileInfo.getBytes()
            );
            ProtocolUtil.writeMessage(out, infoMessage);

            // 读取服务器响应
            FileTransferProtocol.Message response = ProtocolUtil.readMessage(in);
            if (response.getType() == FileTransferProtocol.ERROR) {
                throw new IOException(new String(response.getPayload()));
            }

            // 发送文件数据
            try (FileInputStream fileIn = new FileInputStream(file)) {
                byte[] buffer = new byte[FileTransferProtocol.MAX_PACKET_SIZE - FileTransferProtocol.HEADER_SIZE];
                int bytesRead;
                long totalBytesSent = 0;

                while ((bytesRead = fileIn.read(buffer)) != -1) {
                    byte[] data = new byte[bytesRead];
                    System.arraycopy(buffer, 0, data, 0, bytesRead);
                    
                    FileTransferProtocol.Message dataMessage = new FileTransferProtocol.Message(
                        FileTransferProtocol.FILE_DATA,
                        data
                    );
                    ProtocolUtil.writeMessage(out, dataMessage);
                    
                    totalBytesSent += bytesRead;
                    callback.onProgress(totalBytesSent, file.length());
                }
            }

            // 发送传输完成消息
            FileTransferProtocol.Message completeMessage = new FileTransferProtocol.Message(
                FileTransferProtocol.TRANSFER_COMPLETE,
                null
            );
            ProtocolUtil.writeMessage(out, completeMessage);

            // 等待服务器确认
            response = ProtocolUtil.readMessage(in);
            if (response.getType() == FileTransferProtocol.ERROR) {
                throw new IOException(new String(response.getPayload()));
            }

            callback.onComplete();
        } finally {
            disconnect();
        }
    }

    /**
     * 获取服务器上的文件列表
     * @return 文件名数组，如果获取失败或没有文件则返回空数组
     */
    public String[] getFileList() throws IOException {
        try {
            connect();

            // 请求文件列表
            FileTransferProtocol.Message request = new FileTransferProtocol.Message(
                FileTransferProtocol.REQUEST_FILE_LIST,
                null
            );
            ProtocolUtil.writeMessage(out, request);

            // 读取文件列表
            FileTransferProtocol.Message response = ProtocolUtil.readMessage(in);
            if (response.getType() == FileTransferProtocol.ERROR) {
                String errorMsg = new String(response.getPayload());
                System.err.println("从服务器获取文件列表时发生错误: " + errorMsg);
                throw new IOException(errorMsg);
            }

            String fileListPayload = new String(response.getPayload());
            System.out.println("从服务器接收到的文件列表原始字符串: " + fileListPayload);
            String[] files = fileListPayload.split("\\|");

            System.out.println("解析后的文件列表数组，长度: " + files.length);
            for (int i = 0; i < files.length; i++) {
                System.out.println("文件 " + i + ": " + files[i]);
            }

            if (files.length == 0 || (files.length == 1 && files[0].isEmpty())) {
                 System.out.println("服务器上没有可用的文件");
                 return new String[0]; // 返回空数组
            }

            return files;
        } finally {
            disconnect();
        }
    }

    /**
     * 下载指定文件
     */
    public void downloadSpecificFile(String fileName, String saveDir, ProgressCallback callback) throws IOException {
         try {
            connect();

             // 请求文件
            System.out.println("向服务器请求下载文件: " + fileName);
            FileTransferProtocol.Message request = new FileTransferProtocol.Message(
                FileTransferProtocol.REQUEST_FILE,
                fileName.getBytes()
            );
            ProtocolUtil.writeMessage(out, request);

            // 读取文件信息
            FileTransferProtocol.Message response = ProtocolUtil.readMessage(in);
            if (response.getType() == FileTransferProtocol.ERROR) {
                String errorMsg = new String(response.getPayload());
                 System.err.println("从服务器获取文件信息时发生错误: " + errorMsg);
                throw new IOException(errorMsg);
            }

            String[] fileInfo = new String(response.getPayload()).split("\\|");
             if (fileInfo.length != 2) {
                 String errorMsg = "无效的文件信息格式从服务器: " + new String(response.getPayload());
                 System.err.println(errorMsg);
                 throw new IOException(errorMsg);
             }
            String receivedFileName = fileInfo[0];
            long fileSize = Long.parseLong(fileInfo[1]);
             System.out.println("接收到的文件信息 - 文件名: " + receivedFileName + ", 大小: " + fileSize + " bytes");

             if (!receivedFileName.equals(fileName)) {
                 String errorMsg = "服务器返回的文件名与请求不匹配: 请求=" + fileName + ", 接收=" + receivedFileName;
                 System.err.println(errorMsg);
                 throw new IOException(errorMsg);
             }


            // 创建保存目录
            Files.createDirectories(Paths.get(saveDir));

            // 下载文件
            Path filePath = Paths.get(saveDir, fileName);
             System.out.println("开始下载文件到: " + filePath.toString());
            try (OutputStream fileOut = Files.newOutputStream(filePath)) {
                long totalBytesReceived = 0;

                while (totalBytesReceived < fileSize) {
                    response = ProtocolUtil.readMessage(in);

                    if (response.getType() == FileTransferProtocol.FILE_DATA) {
                        // 避免因接收到比预期更多的数据而导致的无限循环
                        long bytesToRead = Math.min(response.getPayload().length, fileSize - totalBytesReceived);
                        fileOut.write(response.getPayload(), 0, (int) bytesToRead);
                        totalBytesReceived += bytesToRead;

                         // 如果接收到的数据量不足预期，说明可能传输有问题
                        if (bytesToRead < response.getPayload().length) {
                             System.err.println("警告: 接收到的文件数据包大小超出预期，已截断");
                        }

                        callback.onProgress(totalBytesReceived, fileSize);
                    } else if (response.getType() == FileTransferProtocol.ERROR) {
                        String errorMsg = new String(response.getPayload());
                         System.err.println("接收文件数据时服务器报告错误: " + errorMsg);
                         Files.deleteIfExists(filePath); // 出现错误时删除部分传输的文件
                        throw new IOException(errorMsg);
                    } else {
                        String errorMsg = "接收文件数据时收到意外消息类型: " + response.getType();
                         System.err.println(errorMsg);
                         Files.deleteIfExists(filePath); // 出现错误时删除部分传输的文件
                        throw new IOException(errorMsg);
                    }
                }
                 System.out.println("文件数据接收完成");
            }


             // 接收到传输完成消息
            response = ProtocolUtil.readMessage(in);
            if (response.getType() != FileTransferProtocol.TRANSFER_COMPLETE) {
                 String errorMsg = "未收到服务器的传输完成消息，收到类型: " + response.getType();
                 System.err.println(errorMsg);
                 // 不抛出异常，只是警告，因为文件可能已经完整接收
                 // throw new IOException(errorMsg);
            } else {
                 System.out.println("接收到服务器的传输完成消息");
            }


            callback.onComplete();
             System.out.println("文件下载完成");
         } catch (IOException e) {
             System.err.println("下载指定文件时发生IO错误: " + e.getMessage());
            callback.onError("下载文件失败: " + e.getMessage());
            throw e; // 重新抛出以便上层处理
        } catch (Exception e) {
            System.err.println("下载指定文件时发生意外错误: " + e.getMessage());
            e.printStackTrace();
            callback.onError("下载文件失败: " + e.getMessage());
            throw new IOException(e); // 封装为IOException抛出
        }
         finally {
            disconnect();
             System.out.println("下载连接已断开");
        }
    }

    /**
     * 显示文件选择对话框
     * @param files 文件列表
     * @return 用户选择的文件名，如果取消则返回 null
     */
    private String showFileSelectionDialog(String[] files) throws InterruptedException, InvocationTargetException {
         System.out.println("进入 showFileSelectionDialog 方法");
         System.out.println("文件列表长度: " + files.length);
        for (int i = 0; i < files.length; i++) {
             System.out.println("对话框文件 " + i + ": " + files[i]);
        }

        final String[] selectedFile = {null};
        final JDialog dialog = new JDialog((Frame)null, "选择要下载的文件", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(300, 200);
        dialog.setLocationRelativeTo(null);

        JList<String> fileList = new JList<>(files);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(fileList);
        dialog.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        JButton downloadButton = new JButton("下载");
        JButton cancelButton = new JButton("取消");

        downloadButton.addActionListener(e -> {
             System.out.println("点击了下载按钮");
            if (!fileList.isSelectionEmpty()) {
                selectedFile[0] = fileList.getSelectedValue();
                 System.out.println("检测到选中项，值为: " + selectedFile[0]);
            } else {
                 System.out.println("没有检测到选中项");
            }
            dialog.dispose();
             System.out.println("对话框已关闭");
        });

        cancelButton.addActionListener(e -> {
             System.out.println("点击了取消按钮");
            dialog.dispose();
             System.out.println("对话框已关闭");
        });

        buttonPanel.add(downloadButton);
        buttonPanel.add(cancelButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        // 在 EDT 中显示对话框并等待关闭
        System.out.println("在 EDT 中显示对话框");
        SwingUtilities.invokeAndWait(() -> {
            dialog.setVisible(true);
        });
         System.out.println("invokeAndWait 返回，对话框已不再可见");

        return selectedFile[0];
    }

    /**
     * 连接到服务器
     */
    private void connect() throws IOException {
        socket = new Socket(serverAddress, serverPort);
        in = socket.getInputStream();
        out = socket.getOutputStream();
         System.out.println("已连接到服务器: " + serverAddress + ":" + serverPort);
    }

    /**
     * 断开与服务器的连接
     */
    public void disconnect() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
             System.out.println("与服务器的连接已断开");
        } catch (IOException e) {
            System.err.println("关闭连接时发生错误: " + e.getMessage());
            // 忽略关闭时的异常
        }
    }

    /**
     * 进度回调接口
     */
    public interface ProgressCallback {
        void onProgress(long bytesTransferred, long totalBytes);
        void onComplete();
        void onError(String error);
    }
} 