# A program that generates G-Code in a file destination to control Tube-Winder given a set of user entered parameters
# You must be using an anaconda version of Python 3 in order to run this file

import numpy as np


def generator():
# This is the generator fuction which acts  as the UI for the program
    valid = False
    while not valid:
        tube_type = input('Good Morning BBB! What type of tube are you making today? (Enter: "linear" or "non_linear"): ')
        if tube_type == 'linear' or 'non_linear':
            valid = True
        else:
            print('Whoops! Please try again:)')
    
    valid = False
    while not valid:
        destination = input('Would you like the G-Code to be saved as a .txt file or printed to the current terminal? (Enter: "terminal" or "txt"): ')
        if destination == 'terminal' or 'txt':
            valid = True
        else:
            print('Whoops! Please try again:)')

    if tube_type == 'linear' and destination == 'txt':
        p = int_checker('Please enter the cross-section perimiter: ')
        length = int_checker('Please enter the tube length: ')
        wrap_angle = int_checker('Please enter the wrap angle: ')
        tow_width = int_checker('Please enter the tow width')
        tube_thickness = int_checker('Please enter the tube (wall) thickness: ')
        feedrate = int_checker('Please enter the feedrate: ')
        file_name = input('Please enter the name of the file you would like to save the G-Code too (If file does not exist a new text file will be created): ')
        linear_txt(p, length, wrap_angle, tube_thickness, tow_width, feedrate, file_name)
    
    if tube_type == 'non_linear' and destination == 'txt':
        p0 = int_checker('Please enter the cross-section perimiter of the left section: ')
        p1 = int_checker('Please enter the cross-section perimiter of the right section: ')
        length_1 = int_checker('Please enter the length of the left section: ')
        length_2 = int_checker('Please enter the length of the middle (tapered) section: ')
        length_3 = int_checker('Please enter the length of the right section: ')
        wrap_angle = int_checker('Please enter the wrap angle: ')
        tow_width = int_checker('Please enter the tow width')
        tube_thickness = int_checker('Please enter the tube (wall) thickness: ')
        feedrate = int_checker('Please enter the feedrate: ')
        file_name = input('Please enter the name of the file you would like to save the G-Code too (If file does not exist a new text file will be created): ')
        non_linear_txt(p0, p1, length_1, length_2, length_3, wrap_angle, tube_thickness, tow_width, feedrate, file_name)

    if tube_type == 'linear' and destination == 'terminal':
        p = int_checker('Please enter the cross-section perimiter: ')
        length = int_checker('Please enter the tube length: ')
        wrap_angle = int_checker('Please enter the wrap angle: ')
        tow_width = int_checker('Please enter the tow width')
        tube_thickness = int_checker('Please enter the tube (wall) thickness: ')
        feedrate = int_checker('Please enter the feedrate: ')
        linear_terminal(p, length, wrap_angle, tube_thickness, tow_width, feedrate)
    
    if tube_type == 'non_linear' and destination == 'terminal':
        p0 = int_checker('Please enter the cross-section perimiter of the left section: ')
        p1 = int_checker('Please enter the cross-section perimiter of the right section: ')
        length_1 = int_checker('Please enter the length of the left section: ')
        length_2 = int_checker('Please enter the length of the middle (tapered) section: ')
        length_3 = int_checker('Please enter the length of the right section: ')
        wrap_angle = int_checker('Please enter the wrap angle: ')
        tow_width = int_checker('Please enter the tow width')
        tube_thickness = int_checker('Please enter the tube (wall) thickness: ')
        feedrate = int_checker('Please enter the feedrate: ')
        non_linear_terminal(p0, p1, length_1, length_2, length_3, wrap_angle, tube_thickness, tow_width, feedrate)

    if destination == 'txt':
        print(f'G-Code generation complete! The G-Code you requested has been saved to {file_name}. Happy Winding!')
    if destination == 'terminal':
        print('G-Code generation complete! The G-Code you requested has been printed the current terminal. Happy Winding!')



## Writer Functions Below This Line ##
# <=======================================================================================================================================================>



def linear_txt(p, length, wrap_angle, tube_thickness, tow_width, feedrate, file_name):
# This is the  fuction that writes code for linear tubes
    passes = passes_calculator(tube_thickness)
    direction = 1
    current = 0
    while current < passes:
        file_saver(lin_sec(p, length, wrap_angle, direction, feedrate), file_name)

        file_saver(gant_rot(p, tow_width, feedrate, direction), file_name)
        file_saver(gant_rot(p, tow_width, feedrate, direction), file_name)

        direction *= -1



def non_linear_txt(p0, p1, length_1, length_2, length_3, wrap_angle, tube_thickness, tow_width, feedrate, file_name):
# This is the function that write code for non-linear tubes
    passes = passes_calculator(tube_thickness)
    direction = 1
    current = 0
    approx = length_2 // 30
    while current < passes:
        if direction == 1:
            file_saver(lin_sec(p0, length_1, wrap_angle, direction, feedrate), file_name)
        if direction == -1:
            file_saver(lin_sec(p1, length_1, wrap_angle, direction, feedrate), file_name)

        if direction == 1:
            xv = approx
            while xv < length_2:
                file_saver(non_lin_sec(p0, p1, length_2, wrap_angle, direction, feedrate, xv), file_name)
                xv += approx
        if direction == -1:
            xv = length_2 - approx
            while xv > 0:
                file_saver(non_lin_sec(p0, p1, length_2, wrap_angle, direction, feedrate, xv), file_name)
                xv -= approx

        if direction == 1:
            file_saver(lin_sec(p1, length_3, wrap_angle, direction, feedrate), file_name)
        if direction == -1:
            file_saver(lin_sec(p0, length_3, wrap_angle, direction, feedrate), file_name)

        if direction == 1:
            file_saver(gant_rot(p1, tow_width, feedrate, direction), file_name)
            file_saver(gant_rot(p1, tow_width, feedrate, direction), file_name)
        if direction == -1:
            file_saver(gant_rot(p0, tow_width, feedrate, direction), file_name)
            file_saver(gant_rot(p0, tow_width, feedrate, direction), file_name)

        direction *= -1



def linear_terminal(p, length, wrap_angle, tube_thickness, tow_width, feedrate):
# This is the  fuction that writes code for linear tubes
    passes = passes_calculator(tube_thickness)
    direction = 1
    current = 0
    while current < passes:
        print(lin_sec(p, length, wrap_angle, direction, feedrate))

        print(gant_rot(p, tow_width, feedrate, direction))
        print(gant_rot(p, tow_width, feedrate, direction))

        current += 1
        direction *= -1



def non_linear_terminal(p0, p1, length_1, length_2, length_3, wrap_angle, tube_thickness, tow_width, feedrate):
# This is the function that write code for non-linear tubes
    passes = passes_calculator(tube_thickness)
    direction = 1
    current = 0
    approx = 30
    while current < passes:
        if direction == 1:
            print(lin_sec(p0, length_1, wrap_angle, direction, feedrate))
        if direction == -1:
            print(lin_sec(p1, length_1, wrap_angle, direction, feedrate))
        
        if direction == 1:
            segments = length_2 / 5
            pcurrent = p0
            while pcurrent< p1:
                samp = lin_sec(pcurrent, length_2, wrap_angle, direction, feedrate)
                print(samp)
                pcurrent = pcurrent + ((p0+p1)/segments)
        if direction == -1:
            segments = length_2 / 5
            pcurrent = p1
            while pcurrent > p0:
                samp = lin_sec(pcurrent, length_2, wrap_angle, direction, feedrate)
                print(samp)
                pcurrent = pcurrent - ((p0+p1)/segments)

        if direction == 1:
            print(lin_sec(p1, length_3, wrap_angle, direction, feedrate))
        if direction == -1:
            print(lin_sec(p0, length_3, wrap_angle, direction, feedrate))

        if direction == 1:
            print(gant_rot(p1, tow_width, feedrate, direction))
            print(gant_rot(p1, tow_width, feedrate, direction))
        if direction == -1:
            print(gant_rot(p0, tow_width, feedrate, direction))
            print(gant_rot(p0, tow_width, feedrate, direction))
        
        current += 1
        direction *= -1



## Helper Functions Below This Line ##
# <=======================================================================================================================================================>



# This functions returns one line of G-Code for a linear section dependant of direction
# Example command: G1 X(Dest_x) Y(Dest_y) F(Feedrate)
def lin_sec(p, length, wrap_angle, direction, feedrate):
    Dest_X = length
    Dest_Y = ((2 * 180 * (np.tan(np.radians(wrap_angle))) * length) / p)
    if direction == 1:
        return f'G1 X{Dest_X} Y{Dest_Y} F{feedrate}'
    if direction == -1:
        return f'G1 X{-Dest_X} Y{Dest_Y} F{feedrate}'



# This function calculates the length of the winding fuction to constrain the path of the tow according to the formula: 
# [integral <a-b> (sqrt(1 + (d/dx)^2)) =  curve length]
#def line_length(p0, p1, sec_length, wrap_angle):
    #total_length = 0
    #n = 100
    #counter = 0
    #while counter < n:
        #otal_length += np.sqrt( 1 + (((2 * np.pi * np.tan(np.radians(wrap_angle)) * p0) / ((p0 + (counter / sec_length) * (p1 - p0)) ** 2)) ** 2))
        #counter += 1



# This functions returns one line of G-Code for a non-linear section dependant of direction
def non_lin_sec(p0, p1, length, wrap_angle, direction, feedrate, xv, yv, xcurr):
    if direction == 1:
            Dest_x = xv - xcurr
            Dest_y = (2 * 180 * np.tan(np.radians(wrap_angle)) * xv) / (((p1-p0) * np.log(p0 * (xv/length) * (p1 -p0)))) - yv
            return f'G1 X{Dest_x} Y{Dest_y} F{feedrate}'
    if direction == -1:
            Dest_x = length - xv
            Dest_y = (2 * 180 * np.tan(np.radians(wrap_angle)) * xv) / (((p1-p0) * np.log(p0 * (xv/length) * (p1 -p0)))) - yv
            return f'G1 X{-Dest_x} Y{Dest_y} F{feedrate}'
      
# This functions returns one line of G-Code that controls half of the gantry's rotation
# Example command: G1 X(Dest_x) Y(Dest_y) Z(Dest_z) F(Feedrate)
def gant_rot(p, tow_width, feedrate, direction):
    cyc = 1
    Dest_x = 12
    Dest_y = (360 + (360 * (tow_width/p) * 7)) // 2
    Dest_z = 70
    if direction == 1 and cyc == 1:
        cyc *= -1
        return f'G1 X{Dest_x} Y{Dest_y} Z{-Dest_z} F{feedrate}'
    if direction == 1 and cyc == -1:
        cyc *= -1
        return f'G1 X{-Dest_x} Y{Dest_y} Z{-Dest_z} F{feedrate}'
    if direction == -1 and cyc == 1:
        cyc *= -1
        return f'G1 X{-Dest_x} Y{Dest_y} Z{Dest_z} F{feedrate}'
    if direction == -1 and cyc == -1:
        cyc *= -1
        return f'G1 X{Dest_x} Y{Dest_y} Z{Dest_z} F{feedrate}'



# This fuction calcualtes the number of passes required to name a tube of the desired thickness
def passes_calculator(tube_thickness):
    return round((tube_thickness * (1 / 0.18)), 0)



# This fuction is responsible for saving every line of G-Code to a file destination
def file_saver(string, file_name):
    file = open(f'{file_name}', "a")
    file.write(string)
    file.close()


# This function works with the UI to make sure that all inputs are valid and wont break the program
def int_checker(str):
    valid = False
    while not valid:
        value = input(f'{str}')
        if type(value) == float and value > 0:
            valid = True
        else: 
            print('Whoops! Please try again:)')
    return value

#linear_terminal(151, 320, 60, 2, 6.5, 10)
non_linear_terminal(100, 150, 50, 90, 50, 60, 0.18, 6.5, 10)