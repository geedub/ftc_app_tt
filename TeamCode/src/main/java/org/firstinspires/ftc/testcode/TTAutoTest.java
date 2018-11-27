package org.firstinspires.ftc.testcode;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.teamcode.TTHardware;
import org.firstinspires.ftc.teamcode.TTLinearOpMode;


@Autonomous(name = "TT Auto Test", group = "TT")
public class TTAutoTest extends TTLinearOpMode {

    // States
    private enum autoState {
        INITIALIZE,

        LANDING,
        DETECT_MINERAL_MID,
        DETECT_MINERAL_RIGHT,
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


        int READING_SIZE = 100;
        long[] readingArray = new long[READING_SIZE];

        while (!opModeIsActive()) {
            int arrayIndex = (int) System.currentTimeMillis() % READING_SIZE;
            long thisReading = System.currentTimeMillis() % 100;

            readingArray[arrayIndex] = thisReading;

            long readingTotal = 0;
            float readingAvg = 0;
            for (int i = 0; i < READING_SIZE; i++) {
                readingTotal = readingTotal + readingArray[i];
            }
            readingAvg = readingTotal / READING_SIZE;

            telemetry.addData("this reading:", thisReading);
            telemetry.addData("reading Total:", readingTotal);
            telemetry.addData("reading Avg:", readingAvg);
            telemetry.update();
        }



        waitForStart(telemetry);

        // run until the end of the match (driver presses STOP)
        while (opModeIsActive()) {
            telemetry.addData("Status", "Run Time: " + runtime.toString());

            switch (currentState) {
                case INITIALIZE:
                    telemetry.addData("state", currentState.toString());
                    runtime.reset();

                    timeDrive(0.5, 3.0, 0.0);

                    currentState = autoState.STOP;
                    break;

                case LANDING:
                    telemetry.addData("state", currentState.toString());

                    // Lower landing motor
                    robot.motorLanding.setPower(1.0);
                    robot.motorLift.setPower(-0.5);
                    sleep(500);
                    robot.motorLift.setPower(0.0);
                    while (robot.limitLanding.getState() == LIMIT_MAG_OFF && runtime.seconds() < 6.0) {
                        robot.motorLanding.setPower(1.0);
                    }
                    robot.motorLanding.setPower(0.0);

                    timeDrive(0.25, 0.2, 0.0);

                    // Move clear off of landing area
                    timeDrive(0.3, 0.8, -90.0);

                    currentState = autoState.DETECT_MINERAL_MID;
                    break;

                case DETECT_MINERAL_MID:
                    telemetry.addData("state", currentState.toString());

                    gyroHold(0.35, 0.0, 1.0);

                    driveToLine(0.3, 0.0, 1.5);

                    robot.motorExtend.setPower(1.0);
                    robot.motorLift.setPower(0.5);
                    sleep(500);
                    robot.motorLift.setPower(0.0);
                    while (robot.motorExtend.getCurrentPosition() < 4200 && robot.limitExtend.getState() == LIMIT_MAG_OFF) {
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
                        currentState = autoState.DETECT_MINERAL_RIGHT;
                        break;
                    }

                    if (isGold(robot.sensorColorFront)) {
                        telemetry.addData("Found ", "GOLD");
                        goldMineralPos = GoldMineralPos.MIDDLE;
                        currentState = autoState.PUSH_PREP;
                        break;
                    } else {
                        telemetry.addData("Found ", "SILVER");
                        currentState = autoState.DETECT_MINERAL_RIGHT;
                        break;
                    }

                case DETECT_MINERAL_RIGHT:
                    telemetry.addData("state", currentState.toString());

                    timeDrive(0.3, 2.0, 95.0);

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

                case PUSH_PREP:
                    telemetry.addData("state", currentState.toString());

                    while (robot.motorExtend.getCurrentPosition() > 0 && robot.limitExtend.getState() == LIMIT_MAG_OFF) {
                        robot.motorExtend.setPower(-0.5);
                    }
                    robot.motorExtend.setPower(0.0);
                    robot.servoScreen.setPosition(0.8);

                    timeDrive(0.3, 0.5, 90.0);

                    currentState = autoState.PUSH_GOLD;
                    break;

                case PUSH_GOLD:
                    telemetry.addData("state", currentState.toString());

                    timeDrive(0.3, 2.0, 0.0);

                    timeDrive(0.3, 1.3, 180.0);

                    currentState = autoState.PLACE_TEAM_MARKER;
                    break;

                case PLACE_TEAM_MARKER:
                    telemetry.addData("state", currentState.toString());

                    timeDrive(0.3, 3.0, -90.0);
                    if (goldMineralPos.equals(GoldMineralPos.LEFT)) {
                        timeDrive(0.3, 2.5, -90.0);
                    } else if (goldMineralPos.equals(GoldMineralPos.RIGHT)) {
                        timeDrive(0.3, 3.5, -90.0);
                    } else {
                        timeDrive(0.3, 3.0, -90.0);
                    }

                    gyroHold(0.35, 45.0, 1.5);

                    distLeftDrive(0.3, 25, -45.0);

                    timeDrive(0.5, 3.0, -125.0);

                    distRearDrive(0.3, 50, -145.0);

                    // Set marker servo position down to drop team marker
                    robot.servoMarker.setPosition(1.0);

                    currentState = autoState.STOP;
                    break;

                case MOVE_TO_CRATER:
                    telemetry.addData("state", currentState.toString());

                    gyroHold(0.35, 45.0, 1.5);

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
