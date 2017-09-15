package ramo.klevis.ml.kmeans;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.mllib.clustering.KMeans;
import org.apache.spark.mllib.clustering.KMeansModel;
import org.apache.spark.mllib.linalg.Vector;
import org.apache.spark.mllib.linalg.Vectors;


import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;


public class Run {
    public static int[][] imageRGB;
    private static BufferedImage originalImage;

    public static void main(String[] args) throws IOException {

        JavaSparkContext sparkContext = createSparkContext();


        JFrame jFrame = new JFrame();
        jFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        jFrame.setSize(1000, 600);
        jFrame.setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new GridBagLayout());

        ImagePanel sourceImagePanel = new ImagePanel();
        addSourceImagePanel(mainPanel, sourceImagePanel);

        JButton jButton = new JButton("Choose File");
        addChooseButton(mainPanel, jButton);


        JSlider jslider = new JSlider(SwingConstants.VERTICAL, 4, 32, 8);
        jslider.setMajorTickSpacing(4);
        jslider.setMinorTickSpacing(1);
        jslider.setPaintTicks(true);
        jslider.setPaintLabels(true);
        jslider.setToolTipText("Reduce Number of Colors");
        addSlider(mainPanel, jslider);

        ImagePanel transformedImagedPanel = new ImagePanel();
        addTransformedImagePanel(mainPanel, transformedImagedPanel);

        jButton.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser();
                chooser.setCurrentDirectory(new File("target"));
                int i = chooser.showOpenDialog(null);
                if (i == JFileChooser.APPROVE_OPTION) {


                    try {
                        originalImage = ImageIO.read(chooser.getSelectedFile());
                        Image scaledInstance = originalImage.getScaledInstance(ImagePanel.DEFAULT_WIDTH, ImagePanel.DEFAULT_HEIGHT, Image.SCALE_DEFAULT);

                        imageRGB = transformImageToTwoDimensionalMatrix(originalImage);
                        sourceImagePanel.setImg(scaledInstance);
                        mainPanel.updateUI();
                    } catch (IOException e1) {
                        throw new RuntimeException(e1);
                    }
                }
            }
        });

        JButton transform = new JButton("Transform");
        transform.addActionListener(actionListener -> {
            long startBegin = System.currentTimeMillis();
            int colorToReduce = jslider.getValue();
            KMeans kMeans = new KMeans();
            kMeans.setSeed(1).setK(colorToReduce);
            List<Vector> collect = Arrays.stream(imageRGB).map(e -> {
                        DoubleStream doubleStream = Arrays.stream(e).mapToDouble(i -> i);
                        double[] doubles = doubleStream.toArray();
                        Vector dense = Vectors.dense(doubles);
                        return dense;
                    }

            ).collect(Collectors.toList());

            JavaRDD<Vector> parallelize = sparkContext.parallelize(collect);
            KMeansModel fit = kMeans.run(parallelize.rdd());
            Vector[] clusters = fit.clusterCenters();
            long start = System.currentTimeMillis();
            int[][] transformedImage = new int[imageRGB.length][3];
            int index = 0;
            for (int[] ints : imageRGB) {
                double[] doubles = Arrays.stream(ints).mapToDouble(e -> e).toArray();
                int predict = fit.predict(Vectors.dense(doubles));
                transformedImage[index][0] = (int) clusters[predict].apply(0);
                transformedImage[index][1] = (int) clusters[predict].apply(1);
                transformedImage[index][2] = (int) clusters[predict].apply(2);
                index++;
            }
            try {
                reCreateOriginalImageFromMatrix(originalImage, transformedImage, transformedImagedPanel);
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println((System.currentTimeMillis()) - start);
            System.out.println((System.currentTimeMillis()) - startBegin);
        });
        addTransformButton(mainPanel, transform);

        jFrame.add(mainPanel);
        jFrame.setVisible(true);


    }


    private static JavaSparkContext createSparkContext() {
        SparkConf conf = new SparkConf().setAppName("Finance Fraud Detection").setMaster("local[*]");
        return new JavaSparkContext(conf);
    }

    private static void addSourceImagePanel(JPanel mainPanel, JPanel imagePanel) {

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 1;
        c.fill = GridBagConstraints.CENTER;
        c.weightx = 1;
        c.weighty = 1;
        mainPanel.add(imagePanel, c);
        imagePanel.setBorder(new LineBorder(Color.black));
    }

    private static void addTransformedImagePanel(JPanel mainPanel, JPanel imagePanel) {

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 2;
        c.gridy = 1;
        c.fill = GridBagConstraints.CENTER;
        c.weightx = 1;
        c.weighty = 1;
        mainPanel.add(imagePanel, c);
        imagePanel.setBorder(new LineBorder(Color.black));
    }

    private static void addChooseButton(JPanel mainPanel, JButton jButton) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = 2;
        c.fill = GridBagConstraints.CENTER;
        c.weightx = 0;
        c.weighty = 0;
        mainPanel.add(jButton, c);
    }

    private static void addTransformButton(JPanel mainPanel, JButton jButton) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = 0;
        c.fill = GridBagConstraints.CENTER;
        c.weightx = 0;
        c.weighty = 0;
        mainPanel.add(jButton, c);
    }

    private static void addSlider(JPanel mainPanel, JSlider jslider) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = 1;
        c.fill = GridBagConstraints.CENTER;
        c.weightx = 0;
        c.weighty = 0;
        mainPanel.add(jslider, c);
    }

    private static void reCreateOriginalImageFromMatrix(BufferedImage originalImage, int[][] imageRGB, ImagePanel transformedImagedPanel) throws IOException {
        BufferedImage writeBackImage = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_RGB);
        int index = 0;
        for (int i = 0; i < originalImage.getWidth(); i++) {
            for (int j = 0; j < originalImage.getHeight(); j++) {
                Color color = new Color(imageRGB[index][0], imageRGB[index][1], imageRGB[index][2]);
                writeBackImage.setRGB(i, j, color.getRGB());
                index++;
            }
        }
        File outputfile = new File("writeBack.png");
        ImageIO.write(writeBackImage, "png", outputfile);
        transformedImagedPanel.setImage("writeBack.png");
    }

    private static int[][] transformImageToTwoDimensionalMatrix(BufferedImage img) {
        int[][] imageRGB = new int[img.getWidth() * img.getHeight()][3];
        int w = img.getWidth();
        int h = img.getHeight();
        int index = 0;
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                Color color = new Color(img.getRGB(i, j), true);
                imageRGB[index][0] = color.getRed();
                imageRGB[index][1] = color.getGreen();
                imageRGB[index][2] = color.getBlue();
                index++;

            }
        }
        return imageRGB;
    }

}

