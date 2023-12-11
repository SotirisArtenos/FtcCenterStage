package org.firstinspires.ftc.teamcode.teleop.opmodes.test;

import androidx.annotation.Nullable;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;

import org.firstinspires.ftc.ftcdevcommon.AutonomousRobotException;
import org.firstinspires.ftc.ftcdevcommon.Threading;
import org.firstinspires.ftc.ftcdevcommon.platform.android.RobotLogCommon;
import org.firstinspires.ftc.teamcode.auto.FTCAuto;
import org.firstinspires.ftc.teamcode.common.RobotConstants;
import org.firstinspires.ftc.teamcode.robot.FTCRobot;
import org.firstinspires.ftc.teamcode.robot.device.motor.DualMotorMotion;
import org.firstinspires.ftc.teamcode.robot.device.motor.Elevator;
import org.firstinspires.ftc.teamcode.robot.device.motor.SingleMotorMotion;
import org.firstinspires.ftc.teamcode.robot.device.servo.PixelStopperServo;
import org.firstinspires.ftc.teamcode.teleop.common.FTCButton;
import org.firstinspires.ftc.teamcode.teleop.common.FTCToggleButton;
import org.firstinspires.ftc.teamcode.teleop.common.ParallelDrive;
import org.firstinspires.ftc.teamcode.teleop.common.TeleOpWithAlliance;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

public class ElevatorWinchCalibration extends TeleOpWithAlliance {

    private static final String TAG = ElevatorWinchCalibration.class.getSimpleName();

    // Define buttons that return a boolean.
    private final FTCButton elevatorAboveTruss; //**TODO TEMP
    private final FTCButton elevatorOnTruss;
    private final FTCButton winchUp;

    //**TODO START TEMP
    private final FTCButton winchIncrement;
    private final FTCButton winchDecrement;
    private int cumulativeClicks = 0;
    private static final int CLICKS_PER_WINCH_MOVEMENT = 100;
    //**TODO END TEMP

    private final FTCToggleButton toggleSpeed;
    private final FTCButton intake;
    private boolean intakeInProgress = false;
    private final FTCButton reverseIntake;
    private boolean reverseIntakeInProgress = false;
    private final FTCButton outtake;
    private boolean outtakeInProgress = false;
    private final FTCButton deliveryLevel1;
    private final FTCButton deliveryLevel2;

    private final FTCButton goToSafe;
    private final FTCButton goToGround;
    private final FTCButton launchDrone;

    // Drive train
    private double driveTrainPower;
    private double previousDriveTrainPower;
    private final double driveTrainPowerHigh;
    private final double driveTrainPowerLow;
    private final ParallelDrive parallelDrive;

    // Asynchronous
    private enum AsyncAction {MOVE_ELEVATOR_UP, MOVE_ELEVATOR_DOWN_TO_SAFE, NONE}

    private AsyncAction asyncActionInProgress = AsyncAction.NONE;

    // Elevator
    private Elevator.ElevatorLevel currentElevatorLevel = Elevator.ElevatorLevel.GROUND;
    private final double elevatorVelocity;
    private CompletableFuture<Elevator.ElevatorLevel> asyncMoveElevator;

    private PixelStopperServo.PixelServoState pixelServoState;

    public ElevatorWinchCalibration(RobotConstants.Alliance pAlliance,
                                    LinearOpMode pLinearOpMode, FTCRobot pRobot,
                                    @Nullable FTCAuto pAutonomous) {
        super(pAlliance, pLinearOpMode, pRobot, pAutonomous);
        RobotLogCommon.c(TAG, "Constructing CenterStageTeleOp");
        RobotLogCommon.setMostDetailedLogLevel(Objects.requireNonNull(robot.teleOpSettings, "robot.teleOpSettings unexpectedly null").logLevel);

        driveTrainPowerHigh = robot.teleOpSettings.driveTrainPowerHigh;
        driveTrainPower = driveTrainPowerHigh;
        previousDriveTrainPower = driveTrainPower;
        driveTrainPowerLow = robot.teleOpSettings.driveTrainPowerLow;

        // These peripherals can be null in testing if they have been
        // configured out.
        if (robot.elevator != null)
            elevatorVelocity = Objects.requireNonNull(robot.elevator).getVelocity();
        else elevatorVelocity = 0.0;

        // Gamepad 1
        elevatorOnTruss = new FTCButton(linearOpMode, FTCButton.ButtonValue.GAMEPAD_1_LEFT_BUMPER);
        winchUp = new FTCButton(linearOpMode, FTCButton.ButtonValue.GAMEPAD_1_RIGHT_BUMPER);
        toggleSpeed = new FTCToggleButton(linearOpMode, FTCButton.ButtonValue.GAMEPAD_1_A);
        launchDrone = new FTCButton(linearOpMode, FTCButton.ButtonValue.GAMEPAD_1_Y);

        winchIncrement = new FTCButton(linearOpMode, FTCButton.ButtonValue.GAMEPAD_1_X); //**TODO TEMP
        winchDecrement = new FTCButton(linearOpMode, FTCButton.ButtonValue.GAMEPAD_1_B); //**TODO TEMP

        // Gamepad 2
        // Bumpers
        outtake = new FTCButton(linearOpMode, FTCButton.ButtonValue.GAMEPAD_2_RIGHT_BUMPER);
        intake = new FTCButton(linearOpMode, FTCButton.ButtonValue.GAMEPAD_2_LEFT_BUMPER);

        // ABXY Buttons
        goToSafe = new FTCButton(linearOpMode, FTCButton.ButtonValue.GAMEPAD_2_X);
        goToGround = new FTCButton(linearOpMode, FTCButton.ButtonValue.GAMEPAD_2_A);
        reverseIntake = new FTCButton(linearOpMode, FTCButton.ButtonValue.GAMEPAD_2_Y);
        elevatorAboveTruss = new FTCToggleButton(linearOpMode, FTCButton.ButtonValue.GAMEPAD_2_B); //**TODO TEMP for elevaotr/truss calibration

        // D-Pad
        deliveryLevel1 = new FTCButton(linearOpMode, FTCButton.ButtonValue.GAMEPAD_2_DPAD_LEFT);
        deliveryLevel2 = new FTCButton(linearOpMode, FTCButton.ButtonValue.GAMEPAD_2_DPAD_UP);

        // Start the drive train in parallel.
        parallelDrive = new ParallelDrive(linearOpMode, robot.driveTrain, driveTrainPower);
        RobotLogCommon.c(TAG, "Finished constructing CenterStageTeleOp");
    }

    @Override
    public void runTeleOp() throws Exception {
        try {
            // Safety check against the case where the driver hits the small stop
            // button during waitForStart(). We want to make sure that finally()
            // still runs. From the FTC SDK documentation for opModeIsActive():
            // "If this method returns false after waitForStart() has previously
            // been called, you should break out of any loops and allow the OpMode
            // to exit at its earliest convenience."
            if (!linearOpMode.opModeIsActive()) {
                //## Do *not* do this throw new AutonomousRobotException(TAG, "OpMode unexpectedly inactive in runTeleOp()");
                RobotLogCommon.e(TAG, "OpMode unexpectedly inactive in runTeleOp()");
                return;
            }

            // The intake arm must be down before the TeleOp
            // can start.
            if (robot.intakeArmServo != null) // will only be null in testing
                robot.intakeArmServo.down(); // needed only once

            // Set the initial state of the pixel stopper to HOLD
            // so that pixels can be taken in from the front.
            // This will change to RELEASE before outtake out the
            // back.
            if (robot.pixelStopperServo != null) // will only be null in testing
                robot.pixelStopperServo.hold();

            pixelServoState = PixelStopperServo.PixelServoState.HOLD;

            if (robot.droneLauncherServo != null)
                robot.droneLauncherServo.hold();

            //## The drive train thread must be started here because
            // only now does opModeIsActive() return true.
            parallelDrive.startDriveTrain();

            while (linearOpMode.opModeIsActive()) {
                updateButtons();
                updateActions();
            }
        } finally {
            RobotLogCommon.d(TAG, "In finally() block");
        }
    }

    // Update the state of the active buttons. This method should be
    // called once per cycle.
    private void updateButtons() {

        // Game Controller 1
        toggleSpeed.update();
        elevatorOnTruss.update();
        winchUp.update();
        launchDrone.update(); //**TODO raise elevator to over truss position

        elevatorAboveTruss.update(); //**TODO TEMP
        winchIncrement.update(); //**TODO TEMP
        winchDecrement.update(); //**TODO TEMP

        // Game Controller 2
        intake.update();
        reverseIntake.update();
        outtake.update();
        deliveryLevel1.update();
        deliveryLevel2.update();

        goToSafe.update();
        goToGround.update();
    }

    // Execute the actions controlled by Player 1 and Player 2.
    // This method should be called once per cycle.
    private void updateActions() throws Exception {
        updateToggleSpeed();

        if (driveTrainPowerChanged()) {
            parallelDrive.setPower(driveTrainPower);
        }

        // If an asynchronous action is in progress do not allow any
        // actions other than those related to the drive train.
        try {
            switch (asyncActionInProgress) {
                case MOVE_ELEVATOR_UP: {
                    if (asyncMoveElevator.isDone()) {
                        currentElevatorLevel = Threading.getFutureCompletion(asyncMoveElevator);
                        asyncMoveElevator = null;
                        asyncActionInProgress = AsyncAction.NONE;
                        RobotLogCommon.d(TAG, "Async MOVE_ELEVATOR_UP done");
                    } else // the elevator is still moving
                        return; // skip the updates below
                    break;
                }
                case MOVE_ELEVATOR_DOWN_TO_SAFE: {
                    if (asyncMoveElevator.isDone()) {
                        currentElevatorLevel = Threading.getFutureCompletion(asyncMoveElevator);
                        asyncMoveElevator = null;
                        asyncActionInProgress = AsyncAction.NONE;
                        RobotLogCommon.d(TAG, "Async MOVE_ELEVATOR_DOWN done");
                    } else // the elevator is still moving
                        return; // skip the updates below
                    break;
                }
                case NONE: {
                    // continue with updates below
                    break;
                }
                default: {
                    RobotLogCommon.d(TAG, "Invalid async action " + asyncActionInProgress);
                    return; // crashing may leave the elevator in an indeterminate state
                }
            }
        } catch (TimeoutException | IOException ex) {
            // re-throw as unchecked exception
            String eMessage = ex.getMessage() == null ? "**no error message**" : ex.getMessage();
            throw new AutonomousRobotException(TAG, "IOException | TimeoutException " + eMessage);
        }

        // Game Controller 1
        updateElevatorOnTruss();
        updateWinchUp();
        updateLaunchDrone();

        // Game Controller 2
        updateIntake();
        updateReverseIntake();
        updateOuttake();
        updateDeliveryLevel1();
        updateDeliveryLevel2();

        updateElevatorAboveTruss(); //**TODO TEMP
        updateWinchIncrement(); //**TODO TEMP
        updateWinchDecrement(); //**TODO TEMP

        updateGoToSafe();
        updateGoToGround();
    }

    private void updateToggleSpeed() {
        if (toggleSpeed.is(FTCButton.State.TAP)) {
            FTCToggleButton.ToggleState newToggleState = toggleSpeed.toggle();
            if (newToggleState == FTCToggleButton.ToggleState.A) {
                driveTrainPower = driveTrainPowerHigh;
            } else {
                driveTrainPower = driveTrainPowerLow;
            }
        }
    }

    private boolean driveTrainPowerChanged() {
        if (driveTrainPower == previousDriveTrainPower)
            return false;

        previousDriveTrainPower = driveTrainPower;
        return true;
    }

    private void updateElevatorOnTruss() {
        if (elevatorOnTruss.is(FTCButton.State.TAP)) {
            move_elevator_to_selected_level(Elevator.ElevatorLevel.ON_TRUSS);
        }
    }

    private void updateWinchUp() {
        if (winchUp.is(FTCButton.State.TAP)) {
            //**TODO ...
        }
    }

    private void updateLaunchDrone() {
        if (launchDrone.is(FTCButton.State.TAP)) {
            if (!synch_move_elevator_to_drone_launch())
                return;

            robot.droneLauncherServo.launch();
            linearOpMode.sleep(500);

            async_move_elevator_down_to_safe(elevatorVelocity);
        }
    }

    // Continuous intake.
    private void updateIntake() {
        if (intake.is(FTCButton.State.TAP) || intake.is(FTCButton.State.HELD)) {
            if (intake.is(FTCButton.State.TAP)) { // first time
                intakeInProgress = true;

                // Sanity check - make sure the pixel stopper is in the hold position.
                if (pixelServoState != PixelStopperServo.PixelServoState.HOLD) {
                    robot.pixelStopperServo.hold();
                    linearOpMode.sleep(500); // give the servo time to actuate
                    pixelServoState = PixelStopperServo.PixelServoState.HOLD;
                }

                // Note that negative velocity pulls pixels in from the front.
                robot.intakeMotor.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
                robot.intakeMotor.setVelocity(-robot.intakeMotor.velocity);
            }
        } else {
            if (intakeInProgress) {
                intakeInProgress = false;
                robot.intakeMotor.setVelocity(0.0);
            }
        }
    }

    private void updateReverseIntake() {
        if (reverseIntake.is(FTCButton.State.TAP) || reverseIntake.is(FTCButton.State.HELD)) {
            if (reverseIntake.is(FTCButton.State.TAP)) { // first time
                reverseIntakeInProgress = true;

                // Note that positive velocity ejects pixels to the front.
                robot.intakeMotor.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
                robot.intakeMotor.setVelocity(robot.intakeMotor.velocity);
            }
        } else {
            if (reverseIntakeInProgress) {
                reverseIntakeInProgress = false;
                robot.intakeMotor.setVelocity(0.0);
            }
        }
    }

    // Continuous outtake out the back.
    private void updateOuttake() {
        if (outtake.is(FTCButton.State.TAP) || outtake.is(FTCButton.State.HELD)) {
            if (outtake.is(FTCButton.State.TAP)) { // first time
                outtakeInProgress = true;

                // Sanity check - make sure the pixel stopper is in the release position.
                if (pixelServoState != PixelStopperServo.PixelServoState.RELEASE) {
                    robot.pixelStopperServo.release();
                    linearOpMode.sleep(500); // give the servo time to actuate
                    pixelServoState = PixelStopperServo.PixelServoState.RELEASE;
                }

                // Note that with the stopper down negative velocity ejects
                // pixels out the back.
                robot.intakeMotor.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
                robot.intakeMotor.setVelocity(-robot.intakeMotor.velocity);
            }
        } else {
            if (outtakeInProgress) {
                outtakeInProgress = false;

                robot.intakeMotor.setVelocity(0.0);

                // Get ready for the next intake.
                robot.pixelStopperServo.hold();
                linearOpMode.sleep(500); // give the servo time to actuate
                pixelServoState = PixelStopperServo.PixelServoState.HOLD;
            }
        }
    }

    private void updateDeliveryLevel1() {
        if (deliveryLevel1.is(FTCButton.State.TAP)) {
            move_elevator_to_selected_level(Elevator.ElevatorLevel.LEVEL_1);
        }
    }

    private void updateDeliveryLevel2() {
        if (deliveryLevel2.is(FTCButton.State.TAP)) {
            move_elevator_to_selected_level(Elevator.ElevatorLevel.LEVEL_2);
        }
    }

    private void updateElevatorAboveTruss() { //**TODO TEMP
        if (elevatorAboveTruss.is(FTCButton.State.TAP)) {
            move_elevator_to_selected_level(Elevator.ElevatorLevel.ABOVE_TRUSS);
        }
    }

    private void updateWinchIncrement() { //**TODO TEMP
        if (winchIncrement.is(FTCButton.State.TAP)) {
            robot.winchMotion.moveSingleMotor(cumulativeClicks += CLICKS_PER_WINCH_MOVEMENT, robot.winch.getVelocity(),
                    SingleMotorMotion.MotorAction.MOVE_AND_HOLD_VELOCITY);
            updateWinchEncoderTelemetry();
        }
    }

    private void updateWinchDecrement() { //**TODO TEMP
        if (winchDecrement.is(FTCButton.State.TAP)) {
            robot.winchMotion.moveSingleMotor(cumulativeClicks -= CLICKS_PER_WINCH_MOVEMENT, robot.winch.getVelocity(), SingleMotorMotion.MotorAction.MOVE_AND_HOLD_VELOCITY);
            updateWinchEncoderTelemetry();
        }
    }

    private void updateGoToSafe() {
        if (goToSafe.is(FTCButton.State.TAP)) {
            if (asyncActionInProgress != AsyncAction.NONE) {
                RobotLogCommon.d(TAG, "Illegal: asynchronous action " + asyncActionInProgress + " is in progress during a call to updateGoToSafe()");
                return;
            }

            if (currentElevatorLevel == Elevator.ElevatorLevel.SAFE)
                return; // already there

            if (currentElevatorLevel == Elevator.ElevatorLevel.GROUND) { // upward movement?
                robot.elevatorMotion.moveDualMotors(robot.elevator.safe, elevatorVelocity, DualMotorMotion.DualMotorAction.MOVE_AND_HOLD_VELOCITY);
                currentElevatorLevel = Elevator.ElevatorLevel.SAFE;
            } else { // downward movement
                async_move_elevator_down_to_safe(elevatorVelocity);
            }
        }
    }

    private void updateGoToGround() {
        if (goToGround.is(FTCButton.State.TAP)) {
            if (asyncActionInProgress != AsyncAction.NONE) {
                RobotLogCommon.d(TAG, "Illegal: asynchronous action " + asyncActionInProgress + " is in progress during a call to updateGoToGround()");
                return;
            }

            if (currentElevatorLevel == Elevator.ElevatorLevel.GROUND)
                return; // already there

            if (currentElevatorLevel != Elevator.ElevatorLevel.SAFE) {
                RobotLogCommon.d(TAG, "Illegal attempt to move the elevator to ground from " + currentElevatorLevel);
                return; // crashing may leave the elevator in an indeterminate state
            }

            robot.elevatorMotion.moveDualMotors(robot.elevator.ground, elevatorVelocity, DualMotorMotion.DualMotorAction.MOVE_AND_HOLD_VELOCITY);
            currentElevatorLevel = Elevator.ElevatorLevel.GROUND;
        }
    }

    private boolean synch_move_elevator_to_drone_launch() {
        if (asyncActionInProgress != AsyncAction.NONE) {
            RobotLogCommon.d(TAG, "Illegal: asynchronous action " + asyncActionInProgress + " is in progress during a call to move_to_delivery_level()");
            return false;
        }

        // Movement must start at the SAFE level.
        if (currentElevatorLevel != Elevator.ElevatorLevel.SAFE) {
            RobotLogCommon.d(TAG, "Move to delivery level may not start at elevator " + currentElevatorLevel);
            return false;
        }

        linearOpMode.telemetry.addLine("Position for drone launch");
        linearOpMode.telemetry.update();

        RobotLogCommon.d(TAG, "Moving elevator to drone launch level ");
        robot.elevatorMotion.moveDualMotors(robot.elevator.drone, elevatorVelocity, DualMotorMotion.DualMotorAction.MOVE_AND_HOLD_VELOCITY);
        currentElevatorLevel = Elevator.ElevatorLevel.DRONE;
        return true;
    }


    private void move_elevator_to_selected_level(Elevator.ElevatorLevel pDeliverToLevel) {
        if (asyncActionInProgress != AsyncAction.NONE) {
            RobotLogCommon.d(TAG, "Illegal: asynchronous action " + asyncActionInProgress + " is in progress during a call to move_to_delivery_level()");
            return;
        }

        //**TODO TEMP - suspend for calibration test ...
        /*
        // Validate the selected level.
        if (!(pDeliverToLevel == Elevator.ElevatorLevel.LEVEL_1 ||
                pDeliverToLevel == Elevator.ElevatorLevel.LEVEL_2)) {
            RobotLogCommon.d(TAG, "Invalid request to deliver at elevator " + pDeliverToLevel);
            return;
        }

        // Movement must start at the SAFE level.
        if (currentElevatorLevel != Elevator.ElevatorLevel.SAFE) {
            RobotLogCommon.d(TAG, "Move to delivery level may not start at elevator " + currentElevatorLevel);
            return;
        }
         */

        linearOpMode.telemetry.addLine("Position for delivery at " + pDeliverToLevel);
        linearOpMode.telemetry.update();

        RobotLogCommon.d(TAG, "Moving elevator to level " + pDeliverToLevel + " at velocity " + elevatorVelocity);
        switch (pDeliverToLevel) {
            case LEVEL_1: {
                async_move_elevator_up(Objects.requireNonNull(robot.elevator).level_1, elevatorVelocity, Elevator.ElevatorLevel.LEVEL_1);
                break;
            }
            case LEVEL_2: {
                async_move_elevator_up(Objects.requireNonNull(robot.elevator).level_2, elevatorVelocity, Elevator.ElevatorLevel.LEVEL_2);
                break;
            }
            case ON_TRUSS: {
                break; //**TODO
            }
            case ABOVE_TRUSS: {
                break; //**TODO
            }
            default: {
                RobotLogCommon.d(TAG, "Invalid elevator level " + pDeliverToLevel);
                // crashing may leave the elevator in an indeterminate state
            }
        }
    }

    private void async_move_elevator_up(int pElevatorPosition, double pElevatorVelocity, Elevator.ElevatorLevel pElevatorLevelOnCompletion) {
        if (asyncActionInProgress != AsyncAction.NONE) {
            RobotLogCommon.d(TAG, "Async movement already in progress: " + asyncActionInProgress);
            return;
        }

        Callable<Elevator.ElevatorLevel> callableMoveElevatorUp = () -> {
            robot.elevatorMotion.moveDualMotors(pElevatorPosition, pElevatorVelocity, DualMotorMotion.DualMotorAction.MOVE_AND_HOLD_VELOCITY);
            return pElevatorLevelOnCompletion;
        };

        asyncActionInProgress = AsyncAction.MOVE_ELEVATOR_UP;
        RobotLogCommon.d(TAG, "Async move elevator up in progress");
        asyncMoveElevator = Threading.launchAsync(callableMoveElevatorUp);
    }

    private void async_move_elevator_down_to_safe(double pElevatorVelocity) {
        if (asyncActionInProgress != AsyncAction.NONE) {
            RobotLogCommon.d(TAG, "Async movement already in progress: " + asyncActionInProgress);
            return;
        }

        if (!(currentElevatorLevel == Elevator.ElevatorLevel.LEVEL_1 ||
                currentElevatorLevel == Elevator.ElevatorLevel.LEVEL_2 ||
                currentElevatorLevel == Elevator.ElevatorLevel.DRONE)) { // sanity check
            RobotLogCommon.d(TAG, "Illegal attempt to move the elevator down from a level that is not 1 or 2");
            return; // crashing may leave the elevator in an indeterminate state
        }

        Callable<Elevator.ElevatorLevel> callableMoveElevatorDownToSafe = () -> {
            robot.elevatorMotion.moveDualMotors(robot.elevator.safe, pElevatorVelocity, DualMotorMotion.DualMotorAction.MOVE_AND_HOLD_VELOCITY);
            return Elevator.ElevatorLevel.SAFE;
        };

        asyncActionInProgress = AsyncAction.MOVE_ELEVATOR_DOWN_TO_SAFE;
        asyncMoveElevator = Threading.launchAsync(callableMoveElevatorDownToSafe);
        RobotLogCommon.d(TAG, "Async move elevator down in progress");
    }

    private void updateWinchEncoderTelemetry() {
        linearOpMode.telemetry.addData("current", robot.winch.getCurrentPosition(FTCRobot.MotorId.WINCH));
        linearOpMode.telemetry.update();
    }
}