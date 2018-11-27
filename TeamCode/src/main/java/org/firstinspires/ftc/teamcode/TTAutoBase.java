package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.util.ElapsedTime;


import org.firstinspires.ftc.robotcore.external.ClassFactory;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaLocalizer;
import org.firstinspires.ftc.robotcore.external.tfod.Recognition;
import org.firstinspires.ftc.robotcore.external.tfod.TFObjectDetector;

import java.util.List;

@Autonomous(name = "TT Auto Base", group = "TT")
public class TTAutoBase extends TTLinearOpMode {
    private static final String TFOD_MODEL_ASSET = "RoverRuckus.tflite";
    private static final String LABEL_GOLD_MINERAL = "Gold Mineral";
    private static final String LABEL_SILVER_MINERAL = "Silver Mineral";
    public enum Mineral {Gold, Silver};

    // States
    private enum autoState {
        INITIALIZE,

        Sense_Mineral_Tenser,
        LANDING,
        LINE_UP_GOLD,
        DETECT_MINERAL_MID_Sensor_Color,
        DETECT_MINERAL_RIGHT_Sensor_Color,
        PUSH_PREP,

        PUSH_GOLD,
        PLACE_TEAM_MARKER,
        MOVE_TO_CRATER,

        STOP
    }

    private autoState currentState = autoState.INITIALIZE;

    private enum GoldMineralPos {LEFT, MIDDLE, RIGHT, UNKNOWN}

    GoldMineralPos goldMineralPos = GoldMineralPos.UNKNOWN;

    /* Declare OpMode members. */
    private ElapsedTime runtime = new ElapsedTime();


    @Override
    public void runOpMode() {

        /*
         * Initialize the standard drive system variables.
         * The init() method of the hardware class does most of the work here
         */
        robot = new TTHardware();
        robot.init(hardwareMap);

        robot.motorExtend.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        robot.motorLanding.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);

        robot.motorExtend.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        robot.motorLanding.setMode(DcMotor.RunMode.RUN_USING_ENCODER);

        // start calibrating the gyro.
        telemetry.addData(">", "Gyro Calibrating. Do Not move!");
        telemetry.update();

        robot.gyro.calibrate();
        // make sure the gyro is calibrated.
        while (robot.gyro.isCalibrating())  {
            sleep(50);
            idle();
        }
        telemetry.addData(">", "Robot Heading = %d", robot.gyro.getIntegratedZValue());
        telemetry.update();

        robot.servoScreen.setPosition(0.4);

        initVuforia();
        if (ClassFactory.getInstance().canCreateTFObjectDetector()) {
            initTfod();
        } else {
            telemetry.addData("Sorry!", "This device is not compatible with TFOD");
        }
        /** Activate Tensor Flow Object Detection. */
        if (tfod != null) {
            tfod.activate();
        }
        List<Recognition> updatedRecognitions = null;
        while (!opModeIsActive() && !isStopRequested()) {
            if (tfod != null) {
                // getUpdatedRecognitions() will return null if no new information is available since
                // the last time that call was made.
                boolean isGoldDetected = false;
                int gMineralPos = -1;
                int sMineralPos = -1;
                goldMineralPos = GoldMineralPos.UNKNOWN;
                updatedRecognitions = tfod.getUpdatedRecognitions();
                if (updatedRecognitions != null) {
                    telemetry.addData("# Object Detected", updatedRecognitions.size());
                    if (updatedRecognitions.size() == 2) {
                        for (Recognition recognition : updatedRecognitions) {
                            if (recognition.getLabel().equals(LABEL_GOLD_MINERAL)) {
                                isGoldDetected = true;
                            }
                        }
                        if (isGoldDetected) {
                            for (Recognition recognition : updatedRecognitions) {
                                if (recognition.getLabel().equals(LABEL_GOLD_MINERAL)) {
                                    gMineralPos = (int) recognition.getTop();
                                } else {
                                    sMineralPos = (int) recognition.getTop();
                                }
                            }
                            if (gMineralPos < sMineralPos) {
                                goldMineralPos = GoldMineralPos.LEFT;
                            } else {
                                goldMineralPos = GoldMineralPos.MIDDLE;
                            }
                        } else {
                            goldMineralPos = GoldMineralPos.RIGHT;
                        }
                    }
                    telemetry.addData("Gold Pos", goldMineralPos);
                }
            }
            telemetry.update();
        }
        if (tfod != null) {
            tfod.shutdown();
        }

        waitForStart();
        // run until the end of the match (driver presses STOP)
        while (opModeIsActive()) {
            telemetry.addData("Status", "Run Time: " + runtime.toString());

            switch (currentState) {
                case INITIALIZE:
                    telemetry.addData("state", currentState.toString());
                    runtime.reset();

                    robot.servoScreen.setPosition(0.2);

                    currentState = autoState.LANDING;
                    break;

                case LANDING:
                    telemetry.addData("state", currentState.toString());

                    // Lower landing motor
                    robot.motorLanding.setPower(-0.5);
                    robot.motorLift.setPower(-0.5);
                    sleep(500);
                    robot.motorLift.setPower(0.0);
                    while (robot.limitLanding.getState() == LIMIT_MAG_OFF && runtime.seconds() < 2.0) {
                        robot.motorLanding.setPower(-0.5);
                    }
                    robot.motorLanding.setPower(0.0);

//                    timeDrive(0.25, 0.2, 0.0);

                    // Move clear off of landing area
                    timeDrive(0.3, 0.8, -90.0);

                    currentState = autoState.LINE_UP_GOLD;
                    break;

                case DETECT_MINERAL_MID_Sensor_Color:
                    telemetry.addData("state", currentState.toString());

                    gyroHold(0.35, 0.0, 1.0);

                    driveToLine(0.3, 0.0, 1.5);

                    robot.motorExtend.setPower(1.0);
                    robot.motorLift.setPower(0.5);
                    sleep(500);
                    robot.motorLift.setPower(0.0);
                    while (robot.motorExtend.getCurrentPosition() < 3700 && robot.limitExtend.getState() == LIMIT_MAG_OFF) {
                        robot.motorExtend.setPower(0.5);
                    }
                    robot.motorExtend.setPower(0.0);

                    int count = 0;
                    while (count++ < 5 && (Double.isNaN(robot.sensorDistFront.getDistance(DistanceUnit.CM)) || robot.sensorDistFront.getDistance(DistanceUnit.CM) > 25)) {
                        timeDrive(0.3, 0.2, 90.0);
                        telemetry.addData("sensor", "count: " + count + " | dist: " + robot.sensorDistFront.getDistance(DistanceUnit.CM));
                        telemetry.update();
                    }

                    if (Double.isNaN(robot.sensorDistFront.getDistance(DistanceUnit.CM))) {
                        currentState = autoState.DETECT_MINERAL_RIGHT_Sensor_Color;
                        break;
                    }

                    if (isGold(robot.sensorColorFront)) {
                        telemetry.addData("Found ", "GOLD");
                        goldMineralPos = GoldMineralPos.MIDDLE;
                        currentState = autoState.PUSH_PREP;
                        break;
                    } else {
                        telemetry.addData("Found ", "SILVER");
                        currentState = autoState.DETECT_MINERAL_RIGHT_Sensor_Color;
                        break;
                    }

                case DETECT_MINERAL_RIGHT_Sensor_Color:
                    telemetry.addData("state", currentState.toString());

                    timeDrive(0.3, 2.0, 90.0);

                    int countR = 0;
                    while (countR++ < 5 && (Double.isNaN(robot.sensorDistFront.getDistance(DistanceUnit.CM)) || robot.sensorDistFront.getDistance(DistanceUnit.CM) > 25)) {
                        timeDrive(0.3, 0.2, 90.0);
                        telemetry.addData("sensor", "countR: " + countR + " | dist: " + robot.sensorDistFront.getDistance(DistanceUnit.CM));
                        telemetry.update();
                    }

                    if (isGold(robot.sensorColorFront)) {
                        telemetry.addData("Found ", "R GOLD");
                        goldMineralPos = GoldMineralPos.RIGHT;
                    } else {
                        telemetry.addData("Found ", "R SILVER");
                        goldMineralPos = GoldMineralPos.LEFT;
                        timeDrive(0.3, 3.5, -88.0);
                    }
                    currentState = autoState.PUSH_PREP;
                    break;

                case LINE_UP_GOLD:
                    telemetry.addData("state", currentState.toString());

                    if (goldMineralPos.equals(GoldMineralPos.LEFT)) {
                        timeDrive(0.5, 0.8, -30.0);
                    } else if (goldMineralPos.equals(GoldMineralPos.RIGHT)) {
                        timeDrive(0.5, 1.4, 60.0);
                    } else {
                        timeDrive(0.5, 0.8, 20.0);
                    }
                    gyroHold(0.35, 0.0, 1.0);

                    currentState = autoState.PUSH_GOLD;
                    break;

                case PUSH_PREP:
                    telemetry.addData("state", currentState.toString());

//                    while (robot.motorExtend.getCurrentPosition() < -500 && robot.limitExtend.getState() == LIMIT_MAG_OFF) {
//                        robot.motorExtend.setPower(-0.5);
//                    }
                    robot.motorExtend.setPower(0.0);
                    robot.servoScreen.setPosition(0.8);

//                    timeDrive(0.3, 0.5, 90.0);

                    currentState = autoState.PUSH_GOLD;
                    break;

                case PUSH_GOLD:
                    telemetry.addData("state", currentState.toString());

                    // Push gold mineral to base (push to left-half of base)
                    if (goldMineralPos.equals(GoldMineralPos.LEFT)) {
                        timeDrive(0.5, 2.5, 0.0);
                    } else if (goldMineralPos.equals(GoldMineralPos.RIGHT)) {
                        timeDrive(0.5, 2.5, -30.0);
                    } else {
                        timeDrive(0.5, 2.0, -12.0);
                    }

                    currentState = autoState.PLACE_TEAM_MARKER;
                    break;

                case PLACE_TEAM_MARKER:
                    telemetry.addData("state", currentState.toString());

                    gyroHold(0.35, 45.0, 1.0);

                    Double distLeft = robot.rangeLeft.getDistance(DistanceUnit.CM);
                    if (distLeft < 25) {
                        timeDrive(0.35, 0.2, 135.0);
                    } else {
                        timeDrive(0.35, 0.2, -45.0);
                    }

                    driveToLine(0.35, 45.0, 1.0);

                    timeDrive(0.35, 0.2, 45.0);

                    // Set marker servo position down to drop team marker
                    robot.servoMarker.setPosition(1.0);

                    currentState = autoState.MOVE_TO_CRATER;
                    break;

                case MOVE_TO_CRATER:
                    telemetry.addData("state", currentState.toString());

                    gyroHold(0.35, 45.0, 1.0);

                    timeDrive(0.5, 1.0, -125.0);

                    int travelCount = 0;
                    while (travelCount++ < 3) {
                        if (robot.rangeLeft.getDistance(DistanceUnit.CM) > 20) {
                            timeDrive(0.5, 1.0, -110.0);
                        } else if (robot.rangeLeft.getDistance(DistanceUnit.CM) < 7) {
                            timeDrive(0.5, 1.0, -135.0);
                        } else {
                            timeDrive(0.5, 1.0, -125.0);
                        }
                     }

                    currentState = autoState.STOP;
                    break;

                case STOP:
                    telemetry.addData("state", currentState.toString());

                    stop();
                    break;

                default:
                    telemetry.addData("state", currentState.toString());

                    stop();
                    break;
            }

            telemetry.update();

            sleep(100);
            idle();
        }
    }

}
