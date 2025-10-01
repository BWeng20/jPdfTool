package com.bw.jPdfTool;

import com.bw.jPdfTool.model.MergeOptions;

import javax.swing.*;
import java.awt.*;

public class MergeOptionPanel extends JPanel {

    private final JRadioButton append = new JRadioButton("Append");
    private final JRadioButton mix = new JRadioButton("Mix");
    private final JSpinner mixerStart = new JSpinner(new SpinnerNumberModel(2, 1, 100, 1));
    private final JSpinner mixerGapSize = new JSpinner(new SpinnerNumberModel(1, 1, 20, 1));
    private final JSpinner mixerSegSize = new JSpinner(new SpinnerNumberModel(1, 1, 20, 1));

    private final JLabel mergeIconLabel = new JLabel();
    private MergeOptionIcon mergeIcon;
    private int originalPageCount;

    public MergeOptionPanel() {
        super(new GridBagLayout());

        ButtonGroup options = new ButtonGroup();
        options.add(append);
        options.add(mix);

        append.setSelected(true);

        append.addActionListener(e -> updateIcon());
        mix.addActionListener(e -> updateIcon());

        mixerStart.getModel().addChangeListener(e -> updateIcon());
        mixerGapSize.getModel().addChangeListener(e -> updateIcon());
        mixerSegSize.getModel().addChangeListener(e -> updateIcon());

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
        add(mix, gc);

        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0;

        JLabel mixerStartLabel = new JLabel("Insert before Page");
        mixerStartLabel.setLabelFor(mixerStart);
        add(mixerStartLabel, gc);

        gc.weightx = 1;
        gc.gridx = 1;
        add(mixerStart, gc);

        gc.gridy++;

        gc.weightx = 0;
        gc.gridx = 0;
        JLabel mixerSegLabel = new JLabel("<html>Pages in inserted<br>Segments</html>");
        mixerSegLabel.setLabelFor(mixerSegSize);
        add(mixerSegLabel, gc);

        gc.weightx = 1;
        gc.gridx = 1;
        add(mixerSegSize, gc);

        gc.gridy++;

        gc.weightx = 0;
        gc.gridx = 0;
        JLabel mixerGapLabel = new JLabel("<html>Number of Pages<br>between Segments</html>");
        mixerGapLabel.setLabelFor(mixerGapSize);
        add(mixerGapLabel, gc);

        gc.weightx = 1;
        gc.gridx = 1;
        add(mixerGapSize, gc);

        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 2;
        mergeIconLabel.setHorizontalAlignment(JLabel.LEFT);
        mergeIconLabel.setIcon(mergeIcon);
        mergeIconLabel.setToolTipText("<html>The inserted pages do not show the real number<br>as the inserted documents are not yet loaded.</html>");
        add(mergeIconLabel, gc);

        gc.weightx = 0;
        gc.weighty = 1;
        gc.gridy++;
        gc.gridx = 0;
        add(Box.createGlue(), gc);
    }

    protected void updateIcon() {
        boolean enableMixer = mix.isSelected();
        if (mixerStart.isEnabled() != enableMixer) {
            mixerStart.setEnabled(enableMixer);
            mixerGapSize.setEnabled(enableMixer);
            mixerSegSize.setEnabled(enableMixer);
        }
        mergeIcon.setMergeOptions(getMergeOptions());
        mergeIconLabel.repaint();

    }

    public void setOriginalPageCount(int numberOfPages) {
        this.originalPageCount = numberOfPages;
        ((SpinnerNumberModel) mixerStart.getModel()).setMaximum(numberOfPages + 1);
        ((SpinnerNumberModel) mixerGapSize.getModel()).setMaximum(numberOfPages);
        mergeIcon.setMergeOptions(getMergeOptions());
    }

    public MergeOptions getMergeOptions() {
        MergeOptions mergeOptions = new MergeOptions();
        if (append.isSelected()) {
            mergeOptions.startPageNb = originalPageCount + 1;
            mergeOptions.segmentLength = -1;
            mergeOptions.gapLength = -1;
        } else {
            mergeOptions.startPageNb = ((Number) mixerStart.getValue()).intValue();
            mergeOptions.segmentLength = ((Number) mixerSegSize.getValue()).intValue();
            mergeOptions.gapLength = ((Number) mixerGapSize.getValue()).intValue();
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
