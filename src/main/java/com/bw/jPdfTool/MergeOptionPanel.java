package com.bw.jPdfTool;

import com.bw.jPdfTool.model.MergeOptions;

import javax.swing.*;
import java.awt.*;

public class MergeOptionPanel extends JPanel {

    private final JRadioButton append = new JRadioButton("Append");
    private final JRadioButton zipper = new JRadioButton("Zipper");
    private final JSpinner zipperStart = new JSpinner(new SpinnerNumberModel(2, 1, 100, 1));
    private final JSpinner zipperGapSize = new JSpinner(new SpinnerNumberModel(1, 1, 20, 1));
    private final JSpinner zipperSegSize = new JSpinner(new SpinnerNumberModel(1, 1, 20, 1));

    private final JLabel mergeIconLabel = new JLabel();
    private MergeOptionIcon mergeIcon;
    private int originalPageCount;

    public MergeOptionPanel() {
        super(new GridBagLayout());

        ButtonGroup options = new ButtonGroup();
        options.add(append);
        options.add(zipper);

        append.setSelected(true);

        append.addActionListener(e -> updateIcon());
        zipper.addActionListener(e -> updateIcon());

        zipperStart.getModel().addChangeListener(e -> updateIcon());
        zipperGapSize.getModel().addChangeListener(e -> updateIcon());
        zipperSegSize.getModel().addChangeListener(e -> updateIcon());

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 5, 0, 0);
        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0;
        gc.gridy = 0;
        gc.gridwidth = 2;
        add(append, gc);
        gc.gridy++;
        add(zipper, gc);

        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0;

        JLabel zipperStartLabel = new JLabel("Inserted First Page");
        zipperStartLabel.setLabelFor(zipperStart);
        add(zipperStartLabel, gc);

        gc.weightx = 1;
        gc.gridx = 1;
        add(zipperStart, gc);

        gc.gridy++;

        gc.weightx = 0;
        gc.gridx = 0;
        JLabel zipperSegLabel = new JLabel("<html>Pages in inserted<br>Segments</html>");
        zipperSegLabel.setLabelFor(zipperSegSize);
        add(zipperSegLabel, gc);

        gc.weightx = 1;
        gc.gridx = 1;
        add(zipperSegSize, gc);

        gc.gridy++;

        gc.weightx = 0;
        gc.gridx = 0;
        JLabel zipperGapLabel = new JLabel("<html>Number of Pages<br>between Segments</html>");
        zipperGapLabel.setLabelFor(zipperGapSize);
        add(zipperGapLabel, gc);

        gc.weightx = 1;
        gc.gridx = 1;
        add(zipperGapSize, gc);

        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 2;
        mergeIconLabel.setHorizontalAlignment(JLabel.LEFT);
        mergeIconLabel.setIcon(mergeIcon);
        add(mergeIconLabel, gc);

        gc.weightx = 0;
        gc.weighty = 1;
        gc.gridy++;
        gc.gridx = 0;
        add(Box.createGlue(), gc);
    }

    protected void updateIcon() {
        boolean enableZipper = zipper.isSelected();
        if (zipperStart.isEnabled() != enableZipper) {
            zipperStart.setEnabled(enableZipper);
            zipperGapSize.setEnabled(enableZipper);
            zipperSegSize.setEnabled(enableZipper);
        }
        mergeIcon.setMergeOptions(getMergeOptions());
        mergeIconLabel.repaint();

    }

    public void setOriginalPageCount(int numberOfPages) {
        this.originalPageCount = numberOfPages;
        ((SpinnerNumberModel) zipperStart.getModel()).setMaximum(numberOfPages + 1);
        ((SpinnerNumberModel) zipperGapSize.getModel()).setMaximum(numberOfPages);
        mergeIcon.setMergeOptions(getMergeOptions());
    }

    public MergeOptions getMergeOptions() {
        MergeOptions mergeOptions = new MergeOptions();
        if (append.isSelected()) {
            mergeOptions.startPageNb = originalPageCount + 1;
            mergeOptions.segmentLength = -1;
            mergeOptions.gapLength = -1;
        } else {
            mergeOptions.startPageNb = ((Number) zipperStart.getValue()).intValue();
            mergeOptions.segmentLength = ((Number) zipperSegSize.getValue()).intValue();
            mergeOptions.gapLength = ((Number) zipperGapSize.getValue()).intValue();
        }
        return mergeOptions;
    }

    public void install(JFileChooser chooser) {
        chooser.setAccessory(this);
    }

    public void uninstall(JFileChooser chooser) {
        chooser.setAccessory(null);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        mergeIcon = new MergeOptionIcon(this);
        if (mergeIconLabel != null)
            mergeIconLabel.setIcon(mergeIcon);
    }

}
