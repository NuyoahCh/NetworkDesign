package com.networkdesign.server;

import com.networkdesign.protocol.FileTransferProtocol;
import com.networkdesign.util.ProtocolUtil;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 文件传输服务器
 * 处理客户端的文件上传和下载请求
 */
public class FileTransferServer {
    private static final String UPLOAD_DIR = "uploads";  // 文件上传目录
    private final int port;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private volatile boolean running;
    private Thread serverThread;

    public FileTransferServer(int port) {
        this.port = port;
        this.threadPool = Executors.newCachedThreadPool();
        this.running = false;
    }

    /**
     * 启动服务器
     */
    public void start() throws IOException {
        if (running) {
            return; // 服务器已经在运行
        }

        // 创建上传目录
        Files.createDirectories(Paths.get(UPLOAD_DIR));

        try {
            serverSocket = new ServerSocket(port);
            running = true;

            // 在新线程中启动服务器
            serverThread = new Thread(() -> {
                System.out.println("服务器已启动，监听端口: " + port);
                
                // 接受客户端连接
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        System.out.println("接受新的客户端连接: " + clientSocket.getInetAddress());
                        threadPool.execute(() -> handleClient(clientSocket));
                    } catch (IOException e) {
                        if (running) {
                            System.err.println("接受客户端连接时发生错误: " + e.getMessage());
                        }
                    } catch (Exception e) {
                         System.err.println("处理客户端连接时发生意外错误: " + e.getMessage());
                         e.printStackTrace();
                    }
                }
            });
            
            serverThread.start();
        } catch (IOException e) {
            running = false;
            if (serverSocket != null) {
                serverSocket.close();
            }
            throw e;
        } catch (Exception e) {
            System.err.println("启动服务器时发生意外错误: " + e.getMessage());
            running = false;
            if (serverSocket != null) {
                try { serverSocket.close(); } catch (IOException ignored) {}
            }
            throw new IOException(e);
        }
    }

    /**
     * 停止服务器
     */
    public void stop() {
        if (!running) {
            return; // 服务器已经停止
        }

        running = false;
        
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("关闭服务器时发生错误: " + e.getMessage());
        }
        
        // 等待服务器线程结束
        if (serverThread != null && serverThread.isAlive()) {
            try {
                serverThread.join(1000); // 等待最多1秒
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // 关闭线程池
        threadPool.shutdown();
        System.out.println("服务器已停止");
    }

    /**
     * 检查服务器是否正在运行
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 处理客户端请求
     */
    private void handleClient(Socket clientSocket) {
        try (InputStream in = clientSocket.getInputStream();
             OutputStream out = clientSocket.getOutputStream()) {

            // 读取客户端请求
            FileTransferProtocol.Message request = ProtocolUtil.readMessage(in);
            
            // 根据请求类型处理
            switch (request.getType()) {
                case FileTransferProtocol.REQUEST_FILE_LIST:
                    handleFileListRequest(out);
                    break;
                case FileTransferProtocol.REQUEST_FILE:
                    handleFileRequest(request.getPayload(), out);
                    break;
                case FileTransferProtocol.FILE_INFO:
                    handleFileUpload(request.getPayload(), in, out);
                    break;
                case FileTransferProtocol.TRANSFER_COMPLETE:
                    // 客户端发送的传输完成消息，服务器不需要额外处理，连接会关闭
                    break;
                default:
                    sendError(out, "未知的请求类型");
            }

        } catch (IOException e) {
            System.err.println("处理客户端请求时发生错误: " + e.getMessage());
            // 可以发送错误响应给客户端，如果连接还开着的话
            // try { sendError(clientSocket.getOutputStream(), e.getMessage()); } catch (IOException ignored) {}
        } catch (Exception e) {
            System.err.println("处理客户端请求时发生意外错误: " + e.getMessage());
            e.printStackTrace();
             // 可以发送错误响应给客户端，如果连接还开着的话
            // try { sendError(clientSocket.getOutputStream(), "服务器内部错误: " + e.getMessage()); } catch (IOException ignored) {}
        }
        finally {
            try {
                if (!clientSocket.isClosed()) {
                   clientSocket.close();
                }
            } catch (IOException e) {
                // 忽略关闭时的异常
            }
        }
    }

    /**
     * 处理文件列表请求
     */
    private void handleFileListRequest(OutputStream out) throws IOException {
        List<String> files = new ArrayList<>();
        File uploadDir = new File(UPLOAD_DIR);
        if (uploadDir.exists() && uploadDir.isDirectory()) {
            File[] fileList = uploadDir.listFiles();
            if (fileList != null) {
                for (File file : fileList) {
                    if (file.isFile()) {
                        files.add(file.getName());
                    }
                }
            }
        }

        String fileListStr = String.join("|", files);
        FileTransferProtocol.Message response = new FileTransferProtocol.Message(
            FileTransferProtocol.FILE_LIST,
            fileListStr.getBytes()
        );
        ProtocolUtil.writeMessage(out, response);
    }

    /**
     * 处理文件请求
     */
    private void handleFileRequest(byte[] payload, OutputStream out) throws IOException {
        String fileName = new String(payload);
        Path filePath = Paths.get(UPLOAD_DIR, fileName);

        if (!Files.exists(filePath)) {
            sendError(out, "文件不存在: " + fileName);
            return;
        }

        // 发送文件信息
        String fileInfo = fileName + "|" + Files.size(filePath);
        FileTransferProtocol.Message infoMessage = new FileTransferProtocol.Message(
            FileTransferProtocol.FILE_INFO,
            fileInfo.getBytes()
        );
        ProtocolUtil.writeMessage(out, infoMessage);

        // 发送文件数据
        try (InputStream fileIn = Files.newInputStream(filePath)) {
            byte[] buffer = new byte[FileTransferProtocol.MAX_PACKET_SIZE - FileTransferProtocol.HEADER_SIZE];
            int bytesRead;

            while ((bytesRead = fileIn.read(buffer)) != -1) {
                byte[] data = new byte[bytesRead];
                System.arraycopy(buffer, 0, data, 0, bytesRead);
                
                FileTransferProtocol.Message dataMessage = new FileTransferProtocol.Message(
                    FileTransferProtocol.FILE_DATA,
                    data
                );
                ProtocolUtil.writeMessage(out, dataMessage);
            }
        }

         // 文件数据发送完成，发送传输完成消息
        FileTransferProtocol.Message completeMessage = new FileTransferProtocol.Message(
            FileTransferProtocol.TRANSFER_COMPLETE,
            null
        );
        ProtocolUtil.writeMessage(out, completeMessage);
    }

    /**
     * 处理文件上传
     */
    private void handleFileUpload(byte[] payload, InputStream in, OutputStream out) throws IOException {
        String[] fileInfo = new String(payload).split("\\|");
        if (fileInfo.length != 2) {
             sendError(out, "无效的文件信息格式");
             return;
        }
        String fileName = fileInfo[0];
        long fileSize;
        try {
             fileSize = Long.parseLong(fileInfo[1]);
        } catch (NumberFormatException e) {
             sendError(out, "无效的文件大小格式");
             return;
        }
       

        Path filePath = Paths.get(UPLOAD_DIR, fileName);

        // 检查文件是否已存在，避免覆盖
        if (Files.exists(filePath)) {
             sendError(out, "文件已存在: " + fileName);
             return;
        }

         // 告知客户端服务器已准备好接收数据
        FileTransferProtocol.Message readyMessage = new FileTransferProtocol.Message(
             FileTransferProtocol.TRANSFER_COMPLETE, // 使用TRANSFER_COMPLETE作为准备就绪信号
             null
        );
        ProtocolUtil.writeMessage(out, readyMessage);

        try (OutputStream fileOut = Files.newOutputStream(filePath)) {
            long totalBytesReceived = 0;

            while (totalBytesReceived < fileSize) {
                FileTransferProtocol.Message dataMessage = ProtocolUtil.readMessage(in);
                
                if (dataMessage.getType() == FileTransferProtocol.FILE_DATA) {
                    // 避免因接收到比预期更多的数据而导致的无限循环
                    long bytesToRead = Math.min(dataMessage.getPayload().length, fileSize - totalBytesReceived);
                    fileOut.write(dataMessage.getPayload(), 0, (int) bytesToRead);
                    totalBytesReceived += bytesToRead;

                     // 如果接收到的数据量不足预期，说明可能传输有问题
                    if (bytesToRead < dataMessage.getPayload().length) {
                         System.err.println("警告: 接收到的文件数据包大小超出预期，已截断");
                    }

                } else if (dataMessage.getType() == FileTransferProtocol.ERROR) {
                    // 客户端发送错误消息，中断接收
                    System.err.println("客户端报告错误: " + new String(dataMessage.getPayload()));
                    Files.deleteIfExists(filePath); // 删除部分传输的文件
                    throw new IOException("客户端传输错误: " + new String(dataMessage.getPayload()));
                } else {
                    // 接收到非数据或错误消息，中断接收
                     System.err.println("警告: 接收到非数据或错误消息 (类型: " + dataMessage.getType() + ")，中断文件上传");
                     Files.deleteIfExists(filePath); // 删除部分传输的文件
                     throw new IOException("接收到意外消息类型，中断上传");
                }
            }

             // 接收到传输完成消息（可选，取决于客户端是否发送）
            // FileTransferProtocol.Message completeMessage = ProtocolUtil.readMessage(in);
            // if (completeMessage.getType() != FileTransferProtocol.TRANSFER_COMPLETE) {
            //      System.err.println("警告: 未收到客户端的传输完成消息");
            // }

        } catch (IOException e) {
             Files.deleteIfExists(filePath); // 出现异常时删除部分传输的文件
             throw e; // 重新抛出异常以便上层处理和记录
        }
    }

    /**
     * 发送错误消息
     */
    private void sendError(OutputStream out, String errorMessage) throws IOException {
        FileTransferProtocol.Message errorResponse = new FileTransferProtocol.Message(
            FileTransferProtocol.ERROR,
            errorMessage.getBytes()
        );
        ProtocolUtil.writeMessage(out, errorResponse);
    }
} 