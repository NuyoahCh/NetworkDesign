package com.networkdesign.gui;

import com.networkdesign.client.FileTransferClient;
import com.networkdesign.server.FileTransferServer;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files; // 导入 Files 类
import java.nio.file.Paths; // 导入 Paths 类

/**
 * 文件传输GUI主窗口
 * 提供文件上传和下载的图形界面
 */
public class FileTransferGUI extends JFrame {
    private final JTextField serverAddressField;
    private final JTextField serverPortField;
    private final JButton uploadButton;
    private final JButton downloadButton;
    private final JButton serverButton;
    private final JTextArea logArea;
    private FileTransferServer server; // GUI 持有服务器实例，用于启动/停止

    public FileTransferGUI() {
        // 设置窗口属性
        setTitle("文件传输系统");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(null);

        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout());

        // 创建控制面板
        JPanel controlPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 服务器地址输入
        gbc.gridx = 0;
        gbc.gridy = 0;
        controlPanel.add(new JLabel("服务器地址:"), gbc);

        serverAddressField = new JTextField("localhost", 15);
        gbc.gridx = 1;
        controlPanel.add(serverAddressField, gbc);

        // 服务器端口输入
        gbc.gridx = 0;
        gbc.gridy = 1;
        controlPanel.add(new JLabel("服务器端口:"), gbc);

        serverPortField = new JTextField("8888", 15);
        gbc.gridx = 1;
        controlPanel.add(serverPortField, gbc);

        // 上传按钮
        uploadButton = new JButton("上传文件");
        uploadButton.addActionListener(e -> uploadFile());
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        controlPanel.add(uploadButton, gbc);

        // 下载按钮
        downloadButton = new JButton("下载文件");
        downloadButton.addActionListener(e -> downloadFile());
        gbc.gridy = 3;
        controlPanel.add(downloadButton, gbc);

        // 启动/停止服务器按钮
        serverButton = new JButton("启动服务器");
        serverButton.addActionListener(e -> toggleServer());
        gbc.gridy = 4;
        controlPanel.add(serverButton, gbc);

        // 创建日志区域
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);

        // 添加组件到主面板
        mainPanel.add(controlPanel, BorderLayout.WEST);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // 设置主面板
        add(mainPanel);
    }

    /**
     * 上传文件
     */
    private void uploadFile() {
        // 上传前先检查服务器是否运行
        if (server == null || !server.isRunning()) {
            log("错误：请先启动服务器！");
            JOptionPane.showMessageDialog(this, "错误：请先启动服务器！", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            String serverAddress = serverAddressField.getText();
            int serverPort = Integer.parseInt(serverPortField.getText());

            // 创建进度对话框
            TransferProgressDialog progressDialog = new TransferProgressDialog(
                    this, "上传文件", file.getName());

            // 在新线程中执行上传
            new Thread(() -> {
                FileTransferClient client = null; // 线程内部创建客户端
                try {
                    client = new FileTransferClient(serverAddress, serverPort);
                    client.uploadFile(file.getAbsolutePath(),
                            new FileTransferClient.ProgressCallback() {
                                @Override
                                public void onProgress(long bytesTransferred, long totalBytes) {
                                    // 在 EDT 中更新 GUI
                                    SwingUtilities.invokeLater(() ->
                                            progressDialog.updateProgress(bytesTransferred, totalBytes));
                                }

                                @Override
                                public void onComplete() {
                                    // 在 EDT 中更新 GUI
                                    SwingUtilities.invokeLater(() -> {
                                        progressDialog.dispose();
                                        log("文件上传完成: " + file.getName());
                                        JOptionPane.showMessageDialog(null, "文件上传完成: " + file.getName(), "提示", JOptionPane.INFORMATION_MESSAGE);
                                    });
                                }

                                @Override
                                public void onError(String error) {
                                    // 在 EDT 中更新 GUI
                                    SwingUtilities.invokeLater(() -> {
                                        progressDialog.dispose();
                                        log("上传错误: " + error);
                                        JOptionPane.showMessageDialog(null, "上传错误: " + error, "错误", JOptionPane.ERROR_MESSAGE);
                                    });
                                }
                            });
                } catch (IOException e) {
                    // 在 EDT 中更新 GUI
                    SwingUtilities.invokeLater(() -> {
                        progressDialog.dispose();
                        log("上传IO错误: " + e.getMessage());
                        JOptionPane.showMessageDialog(null, "上传IO错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    });
                } catch (Exception e) {
                    // 在 EDT 中更新 GUI
                    SwingUtilities.invokeLater(() -> {
                        progressDialog.dispose();
                        log("上传过程中发生意外错误: " + e.getMessage());
                        e.printStackTrace(); // 打印堆栈跟踪以便调试
                        JOptionPane.showMessageDialog(null, "上传过程中发生意外错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    });
                } finally {
                    if (client != null) {
                        client.disconnect(); // 确保连接关闭
                    }
                }
            }).start();

            progressDialog.setVisible(true); // 显示进度对话框
        }
    }

    /**
     * 下载文件
     */
    private void downloadFile() {
        // 下载前先检查服务器是否运行
        if (server == null || !server.isRunning()) {
            log("错误：请先启动服务器！");
            JOptionPane.showMessageDialog(this, "错误：请先启动服务器！", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String serverAddress = serverAddressField.getText();
        int serverPort = Integer.parseInt(serverPortField.getText());

        // 创建进度对话框 (先显示，表示正在获取文件列表)
        TransferProgressDialog progressDialog = new TransferProgressDialog(
                this, "下载文件", "正在获取文件列表...");
        // progressDialog.setVisible(true); // 先不显示，获取到列表再显示

        // 在新线程中执行下载流程（包括获取文件列表和文件选择）
        new Thread(() -> {
            File saveDir = null;
            String selectedFile = null;
            FileTransferClient client = null; // 线程内部创建客户端

            try {
                client = new FileTransferClient(serverAddress, serverPort);

                // 1. 获取服务器文件列表
                String[] files = client.getFileList();
                client.disconnect(); // 获取完列表后立即断开连接

                if (files == null || files.length == 0) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, "服务器上没有可用的文件", "提示", JOptionPane.INFORMATION_MESSAGE);
                        // 如果进度对话框已经显示了，需要确保它被关闭
                        if(progressDialog.isVisible()) {
                            progressDialog.dispose();
                        }
                        log("服务器上没有可用的文件"); // 在对话框显示后再更新日志
                    });
                    return; // 没有文件可下载
                }

                // 2. 在 EDT 中显示文件选择对话框让用户选择文件
                // 注意：showFileSelectionDialogOnEDT 是阻塞的，当前线程会在这里等待用户操作对话框
                selectedFile = showFileSelectionDialogOnEDT(files);

                if (selectedFile == null) {
                    // 用户取消了文件选择
                    SwingUtilities.invokeLater(() -> {
                        // 如果进度对话框已经显示了，需要确保它被关闭
                        if(progressDialog.isVisible()) {
                            progressDialog.dispose();
                        }
                        log("用户取消了文件选择");
                    });
                    return;
                }

                // 3. 在 EDT 中显示保存目录选择对话框
                // 注意：showSaveDirectoryDialogOnEDT 也是阻塞的
                saveDir = showSaveDirectoryDialogOnEDT();

                if (saveDir == null) {
                    // 用户取消了保存目录选择
                    SwingUtilities.invokeLater(() -> {
                        // 如果进度对话框已经显示了，需要确保它被关闭
                        if(progressDialog.isVisible()) {
                            progressDialog.dispose();
                        }
                        log("用户取消了保存位置选择");
                    });
                    return;
                }

                // 4. 更新并显示进度对话框 (现在才显示，因为用户已经选择了文件和保存位置)
                String finalSelectedFile = selectedFile; // 需要一个 final 变量在 lambda 中使用
                SwingUtilities.invokeLater(() -> {
                    progressDialog.setTitle("下载文件: " + finalSelectedFile);
                    progressDialog.setFileName(finalSelectedFile);
                    progressDialog.setVisible(true); // 现在显示进度对话框
                });

                // 5. 重新创建客户端连接并开始下载选定的文件
                // 这里需要一个新的客户端实例来进行实际的文件数据传输
                FileTransferClient downloadClient = null;
                try {
                    downloadClient = new FileTransferClient(serverAddress, serverPort);
                    downloadClient.downloadSpecificFile(selectedFile, saveDir.getAbsolutePath(), // 调用下载指定文件的方法
                            new FileTransferClient.ProgressCallback() {
                                @Override
                                public void onProgress(long bytesTransferred, long totalBytes) {
                                    // 在 EDT 中更新 GUI
                                    SwingUtilities.invokeLater(() ->
                                            progressDialog.updateProgress(bytesTransferred, totalBytes));
                                }

                                @Override
                                public void onComplete() {
                                    // 在 EDT 中更新 GUI
                                    SwingUtilities.invokeLater(() -> {
                                        progressDialog.dispose();
                                        log("文件下载完成: " + finalSelectedFile); // 使用 final 变量
                                        JOptionPane.showMessageDialog(null, "文件下载完成: " + finalSelectedFile, "提示", JOptionPane.INFORMATION_MESSAGE); // 使用 final 变量
                                    });
                                }

                                @Override
                                public void onError(String error) {
                                    // 在 EDT 中更新 GUI
                                    SwingUtilities.invokeLater(() -> {
                                        progressDialog.dispose();
                                        log("下载错误: " + error);
                                        JOptionPane.showMessageDialog(null, "下载错误: " + error, "错误", JOptionPane.ERROR_MESSAGE);
                                    });
                                }
                            });
                } finally {
                    if (downloadClient != null) {
                        downloadClient.disconnect(); // 确保下载连接关闭
                    }
                }


            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    if(progressDialog.isVisible()) {
                        progressDialog.dispose();
                    }
                    log("下载IO错误: " + e.getMessage());
                    JOptionPane.showMessageDialog(null, "下载IO错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    if(progressDialog.isVisible()) {
                        progressDialog.dispose();
                    }
                    log("下载过程中发生意外错误: " + e.getMessage());
                    e.printStackTrace(); // 打印堆栈跟踪以便调试
                    JOptionPane.showMessageDialog(null, "下载过程中发生意外错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                });
            }
            // Note: The initial 'client' used for getFileList is disconnected in its finally block.
            // The 'downloadClient' is disconnected in its finally block.
            // No need for a top-level finally here unless there's other shared cleanup.
        }).start();
        // progressDialog.setVisible(true); // 不在这里设置可见，在获取到文件列表并用户选择后才显示
    }

    /**
     * 切换服务器状态
     */
    private void toggleServer() {
        if (server == null || !server.isRunning()) {
            try {
                int port = Integer.parseInt(serverPortField.getText());
                // 在新线程中启动服务器以避免阻塞 GUI
                new Thread(() -> {
                    try {
                        server = new FileTransferServer(port);
                        server.start();
                        SwingUtilities.invokeLater(() -> {
                            serverButton.setText("停止服务器");
                            log("服务器已启动，监听端口: " + port);
                        });
                    } catch (IOException e) {
                        SwingUtilities.invokeLater(() -> {
                            log("启动服务器失败: " + e.getMessage());
                            JOptionPane.showMessageDialog(this, "启动服务器失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                        });
                    }
                }).start();
            } catch (NumberFormatException e) {
                log("错误：端口号格式无效！");
                JOptionPane.showMessageDialog(this, "错误：端口号格式无效！", "错误", JOptionPane.ERROR_MESSAGE);
            } catch (Exception e) {
                log("启动服务器发生意外错误: " + e.getMessage());
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "启动服务器发生意外错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            // 在新线程中停止服务器
            new Thread(() -> {
                if (server != null) {
                    server.stop();
                    SwingUtilities.invokeLater(() -> {
                        serverButton.setText("启动服务器");
                        log("服务器已停止");
                    });
                }
            }).start();
        }
    }

    /**
     * 添加日志信息
     */
    private void log(String message) {
        // 在 EDT 中更新日志区域
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            // 自动滚动到最新日志
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    /**
     * 在 EDT 中显示文件选择对话框
     * @param files 文件列表
     * @return 用户选择的文件名，如果取消则返回 null
     */
    private String showFileSelectionDialogOnEDT(String[] files) throws InterruptedException, InvocationTargetException {
        final String[] selected = {null};
        // 使用 invokeAndWait 确保对话框在 EDT 中创建和显示，并且当前线程会等待对话框关闭
        SwingUtilities.invokeAndWait(() -> {
            JDialog dialog = new JDialog(this, "选择要下载的文件", true); // Modality makes it block input to other windows
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setLayout(new BorderLayout());
            dialog.setSize(300, 200);
            dialog.setLocationRelativeTo(this); // Center relative to the main frame

            JList<String> fileList = new JList<>(files);
            fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            JScrollPane scrollPane = new JScrollPane(fileList);
            dialog.add(scrollPane, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel();
            JButton downloadButton = new JButton("下载");
            JButton cancelButton = new JButton("取消");

            downloadButton.addActionListener(e -> {
                if (!fileList.isSelectionEmpty()) {
                    selected[0] = fileList.getSelectedValue();
                }
                dialog.dispose(); // Close the dialog
            });

            cancelButton.addActionListener(e -> {
                dialog.dispose(); // Close the dialog
            });

            buttonPanel.add(downloadButton);
            buttonPanel.add(cancelButton);
            dialog.add(buttonPanel, BorderLayout.SOUTH);

            dialog.setVisible(true); // Show the dialog and block until disposed
        });
        return selected[0];
    }

    /**
     * 在 EDT 中显示保存目录选择对话框
     * @return 用户选择的目录 File 对象，如果取消则返回 null
     */
    private File showSaveDirectoryDialogOnEDT() throws InterruptedException, InvocationTargetException {
        final File[] selectedDir = {null};
        // 使用 invokeAndWait 确保对话框在 EDT 中创建和显示，并且当前线程会等待对话框关闭
        SwingUtilities.invokeAndWait(() -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("选择保存下载文件的目录"); // 设置对话框标题
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); // 只允许选择目录
            // 可以设置默认目录，例如用户的下载目录
            // fileChooser.setCurrentDirectory(new File(System.getProperty("user.home") + "/Downloads"));

            int userSelection = fileChooser.showSaveDialog(this); // 显示保存对话框

            if (userSelection == JFileChooser.APPROVE_OPTION) {
                selectedDir[0] = fileChooser.getSelectedFile();
            }
        });
        return selectedDir[0];
    }


    public static void main(String[] args) {
        // 在 EDT 中运行 GUI
        SwingUtilities.invokeLater(() -> {
            FileTransferGUI gui = new FileTransferGUI();
            gui.setVisible(true);
        });
        System.out.println("服务器准备启动就绪～");
    }
}
