package org.firstinspires.ftc.teamcode.teleop.opmodes.configure;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.util.RobotLog;

import org.firstinspires.ftc.ftcdevcommon.AutonomousRobotException;
import org.firstinspires.ftc.ftcdevcommon.Pair;
import org.firstinspires.ftc.teamcode.common.RobotConstants;
import org.firstinspires.ftc.teamcode.common.RobotConstantsCenterStage;
import org.firstinspires.ftc.teamcode.common.SpikeWindowMapping;
import org.firstinspires.ftc.teamcode.common.xml.SpikeWindowMappingXML;
import org.firstinspires.ftc.teamcode.robot.FTCRobot;
import org.firstinspires.ftc.teamcode.robot.device.camera.CameraStreamProcessor;
import org.firstinspires.ftc.teamcode.robot.device.camera.CameraStreamWebcam;
import org.firstinspires.ftc.teamcode.robot.device.camera.SpikeWindowRendering;
import org.firstinspires.ftc.teamcode.robot.device.camera.VisionPortalWebcamConfiguration;
import org.firstinspires.ftc.teamcode.teleop.common.FTCButton;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Objects;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

// This OpMode gives the drive team a way to check the
// alignment of the front camera to check the OpenCV
// grayscale thresholding of the cropped webcam frame.
//**TODO Later - allow the driver to alter the thresholding
// values via the gamepads.
@TeleOp(name = "PixelCountViewer", group = "Configure")
//@Disabled
public class PixelCountViewer extends LinearOpMode {
    private static final String TAG = PixelCountViewer.class.getSimpleName();

    private CameraStreamProcessor cameraStreamProcessor;
    private EnumMap<RobotConstantsCenterStage.OpMode, SpikeWindowMapping> collectedSpikeWindowMapping;
    private FTCButton opModeBlueA2;
    private FTCButton opModeBlueA4;
    private FTCButton opModeRedF4;
    private FTCButton opModeRedF2;

    // In this OpMode all of the action takes place during init().
    @Override
    public void runOpMode() throws InterruptedException {
        RobotLog.ii(TAG, "Initializing the PixelCountViewer");

        //**TODO Need TeamPropParameters ...

        // Get the camera configuration from RobotConfig.xml.
        FTCRobot robot = new FTCRobot(this, RobotConstants.RunType.TELEOP_VISION_PREVIEW);

        // Start the front webcam with the pixel count window processor.
        if (robot.configuredWebcams == null || robot.configuredWebcams.get(RobotConstantsCenterStage.InternalWebcamId.FRONT_WEBCAM) == null)
            throw new AutonomousRobotException(TAG, "Front camera is not in the current configuration");

        VisionPortalWebcamConfiguration.ConfiguredWebcam frontWebcamConfiguration =
                robot.configuredWebcams.get(RobotConstantsCenterStage.InternalWebcamId.FRONT_WEBCAM);

        cameraStreamProcessor = new CameraStreamProcessor.Builder().build();
        CameraStreamWebcam pixelCountWebcam = new CameraStreamWebcam(frontWebcamConfiguration,
                Pair.create(RobotConstantsCenterStage.ProcessorIdentifier.PIXEL_COUNT, cameraStreamProcessor));

        if (!pixelCountWebcam.waitForWebcamStart(2000))
            throw new AutonomousRobotException(TAG, "Spike window webcam timed out on start");

        Objects.requireNonNull(frontWebcamConfiguration).setVisionPortalWebcam(pixelCountWebcam);
        RobotLog.ii(TAG, "PixelCountViewer successfully started on the front webcam");

        // Note: if no COMPETITION or AUTO_TEST OpMode in RobotAction.XML contains
        // the action FIND_TEAM_PROP then collectedSpikeWindowData will be empty.
        try {
            SpikeWindowMappingXML spikeWindowMappingXML = new SpikeWindowMappingXML(robot.startParameters.robotActionFilename);
            collectedSpikeWindowMapping = spikeWindowMappingXML.collectSpikeWindowMapping();
        } catch (ParserConfigurationException | IOException | SAXException |
                 XPathExpressionException ex) {
            throw new AutonomousRobotException(TAG, ex.getMessage());
        }

        // Set up the DPAD buttons for starting position selection - clockwise
        // from the audience wall.
        opModeBlueA2 = new FTCButton(this, FTCButton.ButtonValue.GAMEPAD_1_A);
        opModeBlueA4 = new FTCButton(this, FTCButton.ButtonValue.GAMEPAD_1_X);
        opModeRedF4 = new FTCButton(this, FTCButton.ButtonValue.GAMEPAD_1_Y);
        opModeRedF2 = new FTCButton(this, FTCButton.ButtonValue.GAMEPAD_1_B);

        telemetry.addData("DS preview on/off", "3 dots, Camera Stream");
        telemetry.addData(">", "Touch play to *END* the OpMode");
        telemetry.update();

        while (!isStarted() && !isStopRequested()) {
            updateButtons();
            updatePlayerOne();
        }

        telemetry.addLine("Ending the SpikeWindowViewer");
        telemetry.update();
    }

    private void updateButtons() {
        opModeBlueA2.update();
        opModeBlueA4.update();
        opModeRedF4.update();
        opModeRedF2.update();
    }

    private void updatePlayerOne() {
        updateOpModeBlueA2();
        updateOpModeBlueA4();
        updateOpModeRedF4();
        updateOpModeRedF2();
    }

    private void updateOpModeBlueA2() {
        setPixelCountRendering(RobotConstantsCenterStage.OpMode.BLUE_A2, opModeBlueA2);
    }

    private void updateOpModeBlueA4() {
        setPixelCountRendering(RobotConstantsCenterStage.OpMode.BLUE_A4, opModeBlueA4);
    }

    private void updateOpModeRedF4() {
        setPixelCountRendering(RobotConstantsCenterStage.OpMode.RED_F4, opModeRedF4);
    }

    private void updateOpModeRedF2() {
        setPixelCountRendering(RobotConstantsCenterStage.OpMode.RED_F2, opModeRedF2);
    }

    private void setPixelCountRendering(RobotConstantsCenterStage.OpMode pOpMode, FTCButton pOpModeButton) {
        if (pOpModeButton.is(FTCButton.State.TAP)) {
            RobotLog.dd(TAG, "Button " + pOpModeButton.getButtonValue() + " for " + pOpMode + " tapped");

            // Make sure that the Autonomous OpMode for the selected
            // starting position has actually been defined in RobotAction.xml.
            SpikeWindowMapping spikeWindows = collectedSpikeWindowMapping.get(pOpMode);
            if (spikeWindows == null)
                return; // ignore the button click

            //**TODO Show the pixel count grayscale thresholding on the
            // Driver Station camera stream. NEED PixelCountRendering
            /*
            *need* alliance
                public TeamPropReturn recognizeTeamProp(*not needed* ImageProvider pImageProvider,
                                            *not needed* RobotConstantsCenterStage.TeamPropRecognitionPath pTeamPropRecognitionPath,
                                            *need pTeamPropParameters.colorChannelPixelCountParameters* TeamPropParameters pTeamPropParameters,
                                            *need* SpikeWindowMapping pSpikeWindowMapping) throws InterruptedException {

             */
            cameraStreamProcessor.setCameraStreamRendering(new SpikeWindowRendering(spikeWindows));
            RobotLog.dd(TAG, "Set spike window mapping for " + pOpMode);
            telemetry.addLine("Spike windows for " + pOpMode);
            telemetry.update();
        }
    }

}