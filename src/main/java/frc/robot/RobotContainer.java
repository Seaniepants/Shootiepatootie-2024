// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.wpilibj.motorcontrol.Spark;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import frc.robot.Constants.AutoConstants;
import frc.robot.Constants.DriveConstants;
import frc.robot.Constants.OIConstants;
import frc.robot.commands.ElevatorPIDCommand;
import frc.robot.Constants.ElevatorConstants;
import frc.robot.subsystems.DriveSubsystem;
import frc.robot.subsystems.Elevator;
import frc.robot.subsystems.Intake;
import io.github.oblarg.oblog.Logger;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.RunCommand;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import edu.wpi.first.wpilibj2.command.SwerveControllerCommand;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;

/*
 * This class is where the bulk of the robot should be declared.  Since Command-based is a
 * "declarative" paradigm, very little robot logic should actually be handled in the {@link Robot}
 * periodic methods (other than the scheduler calls).  Instead, the structure of the robot
 * (including subsystems, commands, and button mappings) should be declared here.
 */
public class RobotContainer {
  // The robot's subsystems
  private final DriveSubsystem robotDrive = new DriveSubsystem();
  private final Elevator elevator = new Elevator();
  private final Intake intake = new Intake();
  private Spark blinkin = new Spark(5);

  // The driver's controller
  CommandXboxController driverController = new CommandXboxController(OIConstants.kDriverControllerPort);
  // Secondary controller
  CommandXboxController secondaryController = new CommandXboxController(OIConstants.kSecondaryControllerPort);

  SendableChooser<SequentialCommandGroup> autoSelector = new SendableChooser<>();

  /**
   * The container for the robot. Contains subsystems, OI devices, and commands.
   */
  public RobotContainer() {
    // Configure the button bindings
    configureButtonBindings();
    Logger.configureLoggingAndConfig(this, true);

    // Configure default commands
    robotDrive.setDefaultCommand(
        // The left stick controls translation of the robot.
        // Turning is controlled by the X axis of the right stick.
        new RunCommand(
            () -> robotDrive.drive(
                -MathUtil.applyDeadband(driverController.getLeftY() /2, OIConstants.kDriveDeadband),
                -MathUtil.applyDeadband(driverController.getLeftX() /2, OIConstants.kDriveDeadband),
                -MathUtil.applyDeadband(driverController.getRightX() /2, OIConstants.kDriveDeadband),
                true, true),
            robotDrive));

    elevator.setDefaultCommand(
      new RunCommand(
        () -> { 

          double powerIn = -MathUtil.applyDeadband(secondaryController.getRightY() / 2, .1);
          // Booleans to limit elevator movment
          boolean goingDown = -secondaryController.getRightY() < 0;
          boolean goingUp = -secondaryController.getRightY() > 0;
          boolean aboveLimit = elevator.getElevatorLeftEncoder() > ElevatorConstants.elevatorTopLimit;
          
          double limitedPower = (elevator.atBottom() && goingDown) || (aboveLimit && goingUp) ? 0 : powerIn;
          
          elevator.setMotor(limitedPower); 
        }, elevator)
    );

    
    intake.setDefaultCommand(
      new RunCommand( () -> {

        double primary = driverController.getLeftTriggerAxis() - driverController.getRightTriggerAxis();
        double secondary = -secondaryController.getLeftY();
        intake.setIntakeSpeeds(
          MathUtil.applyDeadband(primary + secondary, 0.08), 
          MathUtil.applyDeadband(primary + secondary, 0.08));
      }, intake)
      );
      
    autoSelector.setDefaultOption("Slow forward 5 meters", new SlowForward5Meters());
  }

  /**
   * Use this method to define your button->command mappings. Buttons can be
   * created by
   * instantiating a {@link edu.wpi.first.wpilibj.GenericHID} or one of its
   * subclasses ({@link
   * edu.wpi.first.wpilibj.Joystick} or {@link XboxController}), and then calling
   * passing it to a
   * {@link JoystickButton}.
   */
  private void configureButtonBindings() {

    /*◇─◇──◇─◇
     Drivetrain
    ◇─◇──◇─◇*/

    driverController.x()
        .whileTrue(new RunCommand(
            () -> robotDrive.setX(),
            robotDrive));

    driverController.start().onTrue(new InstantCommand(()->robotDrive.zeroHeading()));
            
    /*◇─◇──◇─◇
      Elevator
    ◇─◇──◇─◇*/

    // Bottom Setpoint
    secondaryController.povDown().onTrue(new InstantCommand(
      () -> elevator.setSetpointCycleIndex(0)
    ).andThen(new ElevatorPIDCommand(elevator)));

    // Shelf 1 Setpoint
    secondaryController.a().onTrue(new InstantCommand(
      () -> elevator.setSetpointCycleIndex(1)
    ).andThen(new ElevatorPIDCommand(elevator)));

    // Shelf 2 Setpoint
    secondaryController.x().onTrue(new InstantCommand(
      () -> elevator.setSetpointCycleIndex(1)
    ).andThen(new ElevatorPIDCommand(elevator)));

    // Shelf 3 Setpoint
    secondaryController.y().onTrue(new InstantCommand(
      () -> elevator.setSetpointCycleIndex(1)
    ).andThen(new ElevatorPIDCommand(elevator)));

    // Shelf 4 Setpoint
    secondaryController.rightBumper().onTrue(new InstantCommand(
      () -> elevator.setSetpointCycleIndex(1)
    ).andThen(new ElevatorPIDCommand(elevator)));

    // Top Setpoint
    secondaryController.povUp().onTrue(new InstantCommand(
      () -> elevator.setSetpointCycleIndex(ElevatorConstants.elevatorSetpoints.length - 1)
    ).andThen(new ElevatorPIDCommand(elevator)));

    secondaryController.b().onTrue(elevator.stopElevator());

  }
  public void updateLogger() {
    Logger.updateEntries();
  }

  public void setBlinkin (double pwm) {
    blinkin.set(pwm);
  }


  /**
   * Use this to pass the autonomous command to the main {@link Robot} class.
   *
   * @return the command to run in autonomous
   */
  public Command getAutonomousCommand() {
    return autoSelector.getSelected();
  }

  /*◇─◇──◇─◇
      Autos
  ◇─◇──◇─◇*/
  
  private class SlowForward5Meters extends SequentialCommandGroup {
    
    public SlowForward5Meters(){
      addCommands(
        new InstantCommand( () -> {
          robotDrive.resetOdometry(Trajectories.slowForward4Meters().getInitialPose());
        }),

        new SwerveControllerCommand(
          Trajectories.slowForward4Meters(),
          robotDrive::getPose, // Functional interface to feed supplier
          DriveConstants.kDriveKinematics,

          // Position controllers
          new PIDController(AutoConstants.kPXController, 0, 0),
          new PIDController(AutoConstants.kPYController, 0, 0),
          Trajectories.getDefaultThetaController(),
          robotDrive::setModuleStates,
          robotDrive)
      );
    }
  }
}
