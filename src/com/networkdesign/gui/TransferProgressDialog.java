package com.networkdesign.gui;

import javax.swing.*;
import java.awt.*;

/**
 * 文件传输进度对话框
 * 显示文件传输的进度条和状态信息
 */
public class TransferProgressDialog extends JDialog {
    private final JProgressBar progressBar;
    private final JLabel statusLabel;
    private final JLabel fileNameLabel;
    private final JLabel speedLabel;
    private long startTime;
    private long lastBytesTransferred;
    private long totalBytesTransferred;

    public TransferProgressDialog(Frame parent, String title, String fileName) {
        super(parent, title, true);
        this.totalBytesTransferred = 0;
        this.lastBytesTransferred = 0;
        this.startTime = System.currentTimeMillis();

        // 设置对话框属性
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        setSize(400, 150);
        setLocationRelativeTo(parent);
        setResizable(false);

        // 创建面板
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 文件名标签
        fileNameLabel = new JLabel("文件: " + fileName);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(fileNameLabel, gbc);

        // 进度条
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        gbc.gridy = 1;
        panel.add(progressBar, gbc);

        // 状态标签
        statusLabel = new JLabel("准备传输...");
        gbc.gridy = 2;
        panel.add(statusLabel, gbc);

        // 速度标签
        speedLabel = new JLabel("传输速度: 0 KB/s");
        gbc.gridy = 3;
        panel.add(speedLabel, gbc);

        // 取消按钮
        JButton cancelButton = new JButton("取消");
        cancelButton.addActionListener(e -> dispose());
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(cancelButton, gbc);

        add(panel);
    }

    /**
     * 更新传输进度
     * @param bytesTransferred 已传输的字节数
     * @param totalBytes 总字节数
     */
    public void updateProgress(long bytesTransferred, long totalBytes) {
        this.totalBytesTransferred = bytesTransferred;
        
        // 计算进度百分比
        int progress = (int) ((bytesTransferred * 100) / totalBytes);
        progressBar.setValue(progress);
        
        // 计算传输速度
        long currentTime = System.currentTimeMillis();
        long timeElapsed = currentTime - startTime;
        if (timeElapsed > 0) {
            long bytesPerSecond = (bytesTransferred * 1000) / timeElapsed;
            String speedText = formatSpeed(bytesPerSecond);
            speedLabel.setText("传输速度: " + speedText);
        }
        
        // 更新状态
        String status = String.format("已传输: %s / %s", 
            formatSize(bytesTransferred), 
            formatSize(totalBytes));
        statusLabel.setText(status);
    }

    /**
     * 设置文件名称标签的文本
     * @param fileName 文件名
     */
    public void setFileName(String fileName) {
        fileNameLabel.setText("文件: " + fileName);
    }

    /**
     * 格式化文件大小
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * 格式化传输速度
     */
    private String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond < 1024) {
            return bytesPerSecond + " B/s";
        } else if (bytesPerSecond < 1024 * 1024) {
            return String.format("%.1f KB/s", bytesPerSecond / 1024.0);
        } else {
            return String.format("%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0));
        }
    }
} 