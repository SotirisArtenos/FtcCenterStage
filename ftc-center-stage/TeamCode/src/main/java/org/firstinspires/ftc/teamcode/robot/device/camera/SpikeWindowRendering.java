package org.firstinspires.ftc.teamcode.robot.device.camera;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import org.firstinspires.ftc.ftcdevcommon.Pair;
import org.firstinspires.ftc.teamcode.common.RobotConstantsCenterStage;
import org.firstinspires.ftc.teamcode.common.SpikeWindowMapping;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SpikeWindowRendering implements CameraStreamRendering {

    private final SpikeWindowMapping spikeWindowMapping;

    public SpikeWindowRendering(SpikeWindowMapping pSpikeWindowMapping) {
        spikeWindowMapping = pSpikeWindowMapping;
    }

    public List<String> renderFrameToCanvas(Mat pWebcamFrame, Canvas pDriverStationScreenCanvas,
                                            int onscreenWidth, int onscreenHeight) {
        Pair<Rect, RobotConstantsCenterStage.TeamPropLocation> leftWindow = spikeWindowMapping.spikeWindows.get(RobotConstantsCenterStage.SpikeLocationWindow.LEFT);
        float xFactor = onscreenWidth / (float) spikeWindowMapping.imageParameters.resolution_width;
        float yFactor = onscreenHeight / (float) spikeWindowMapping.imageParameters.resolution_height;

        // Draw the ROI on the canvas.
        Paint greenAxisPaint = new Paint();
        greenAxisPaint.setColor(Color.GREEN);
        greenAxisPaint.setAntiAlias(true);
        greenAxisPaint.setStyle(Paint.Style.STROKE);
        greenAxisPaint.setStrokeCap(Paint.Cap.BUTT);
        greenAxisPaint.setStrokeWidth(8);

        float left = spikeWindowMapping.imageParameters.image_roi.x * xFactor;
        float top = spikeWindowMapping.imageParameters.image_roi.y * yFactor;
        float right = left + (spikeWindowMapping.imageParameters.image_roi.width * xFactor);
        float bottom = top + (spikeWindowMapping.imageParameters.image_roi.height * yFactor);
        pDriverStationScreenCanvas.drawRect(left, top, right, bottom, greenAxisPaint);

        // Draw the vertical line that separates the left and right
        // spike windows.
        float spikeWindowBoundaryX = left + (Objects.requireNonNull(leftWindow).first.width * xFactor);
        pDriverStationScreenCanvas.drawLine(spikeWindowBoundaryX, top, spikeWindowBoundaryX, bottom, greenAxisPaint);
        return new ArrayList<String>(); // nothing for telemetry
    }

}
