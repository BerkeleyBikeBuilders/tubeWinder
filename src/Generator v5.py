# A program that generates G-Code in a file destination to control Tube-Winder given a set of user entered parameters
# You must be using an anaconda version of Python 3 in order to run this file

import numpy as np


def generator():
# This is the generator fuction which acts  as the UI for the program
    valid = False
    while not valid:
        tube_type = input('Good Morning BBB! What type of tube are you making today? (Enter: "linear" or "non_linear"): ')
        if tube_type == 'linear' or tube_type == 'non_linear':
            valid = True
        else:
            print('Whoops! Please try again:)')
    
    valid = False
    while not valid:
        destination = input('Would you like the G-Code to be saved as a .txt file or printed to the current terminal? (Enter: "terminal" or "txt"): ')
        if destination == 'terminal' or destination == 'txt':
            valid = True
        else:
            print('Whoops! Please try again:)')

    if tube_type == 'linear' and destination == 'txt':
        p = int_checker('Please enter the cross-section perimiter: ')
        length = int_checker('Please enter the tube length: ')
        wrap_angle = int_checker('Please enter the wrap angle: ')
        tow_width = int_checker('Please enter the tow width: ')
        tube_thickness = int_checker('Please enter the tube (wall) thickness: ')
        feedrate = int_checker('Please enter the feedrate: ')
        file_name = input('Please enter the name of the file you would like to save the G-Code too (If file does not exist a new text file will be created) Ex.(___.txt): ')
        linear_txt(p, length, wrap_angle, tube_thickness, tow_width, feedrate, file_name)
    
    if tube_type == 'non_linear' and destination == 'txt':
        p0 = int_checker('Please enter the cross-section perimiter of the left section: ')
        p1 = int_checker('Please enter the cross-section perimiter of the right section: ')
        length_1 = int_checker('Please enter the length of the left section: ')
        length_2 = int_checker('Please enter the length of the middle (tapered) section: ')
        length_3 = int_checker('Please enter the length of the right section: ')
        wrap_angle = int_checker('Please enter the wrap angle: ')
        tow_width = int_checker('Please enter the tow width: ')
        tube_thickness = int_checker('Please enter the tube (wall) thickness: ')
        feedrate = int_checker('Please enter the feedrate: ')
        file_name = input('Please enter the name of the file you would like to save the G-Code too (If file does not exist a new text file will be created) Ex.(___.txt): ')
        non_linear_txt(p0, p1, length_1, length_2, length_3, wrap_angle, tube_thickness, tow_width, feedrate, file_name)

    if tube_type == 'linear' and destination == 'terminal':
        p = int_checker('Please enter the cross-section perimiter: ')
        length = int_checker('Please enter the tube length: ')
        wrap_angle = int_checker('Please enter the wrap angle: ')
        tow_width = int_checker('Please enter the tow width: ')
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
        tow_width = int_checker('Please enter the tow width: ')
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
    file_saver((f'G21 G91 F{feedrate}'), file_name)
    file_saver((f'G01 Y360 Z{wrap_angle}'), file_name)
    passes = passes_calculator(tube_thickness, length, p, tow_width, wrap_angle)
    direction = 1
    current = 0
    while current < passes:
        file_saver(lin_sec(p, length, wrap_angle, direction), file_name)

        cyc = 1
        file_saver(gant_rot(p, tow_width, wrap_angle, direction, cyc), file_name)
        cyc = -1
        file_saver(gant_rot(p, tow_width, wrap_angle, direction, cyc), file_name)

        direction *= -1
        current += 1



def non_linear_txt(p0, p1, length_1, length_2, length_3, wrap_angle, tube_thickness, tow_width, feedrate, file_name):
# This is the function that write code for non-linear tubes
    file_saver((f'G21 G91 F{feedrate}'), file_name)
    file_saver((f'G01 Y360 Z{wrap_angle}'), file_name)
    passes = passes_calculator(tube_thickness, length_1 + length_2 + length_3, p0, tow_width, wrap_angle)
    direction = 1
    current = 0 
    while current < passes:
        if direction == 1:
            file_saver(lin_sec(p0, length_1, wrap_angle, direction), file_name)
        if direction == -1:
            file_saver(lin_sec(p1, length_1, wrap_angle, direction), file_name)
        
        if direction == 1:
            segments = length_2 / 5
            pcurrent = p0
            while pcurrent< p1:
                samp = lin_sec(pcurrent, 5, wrap_angle, direction)
                file_saver((samp), file_name)
                pcurrent = pcurrent + ((p1-p0)/segments)
        if direction == -1:
            segments = length_2 / 5
            pcurrent = p1
            while pcurrent > p0:
                samp = lin_sec(pcurrent, 5, wrap_angle, direction)
                file_saver((samp), file_name)
                pcurrent = pcurrent - ((p1-p0)/segments)

        if direction == 1:
            file_saver(lin_sec(p1, length_3, wrap_angle, direction), file_name)
        if direction == -1:
            file_saver(lin_sec(p0, length_3, wrap_angle, direction), file_name)

        if direction == 1:
            cyc = 1
            file_saver(gant_rot(p1, tow_width, wrap_angle, direction, cyc), file_name)
            cyc = -1
            file_saver(gant_rot(p1, tow_width, wrap_angle, direction, cyc), file_name)
        if direction == -1:
            cyc = 1
            file_saver(gant_rot(p0, tow_width, wrap_angle, direction, cyc), file_name)
            cyc = -1
            file_saver(gant_rot(p0, tow_width, wrap_angle, direction, cyc), file_name)
        
        current += 1
        direction *= -1



def linear_terminal(p, length, wrap_angle, tube_thickness, tow_width, feedrate):
# This is the  fuction that writes code for linear tubes
    print(f'G21 G91 F{feedrate}')
    print(f'G01 Y360 Z{wrap_angle}')
    passes = passes_calculator(tube_thickness, length, p, tow_width, wrap_angle)
    direction = 1
    current = 0
    while current < passes:
        print(lin_sec(p, length, wrap_angle, direction))

        cyc = 1
        print(gant_rot(p, tow_width, wrap_angle, direction, cyc))
        cyc = -1
        print(gant_rot(p, tow_width, wrap_angle, direction, cyc))

        current += 1
        direction *= -1



def non_linear_terminal(p0, p1, length_1, length_2, length_3, wrap_angle, tube_thickness, tow_width, feedrate):
# This is the function that write code for non-linear tubes
    print(f'G21 G91 F{feedrate}')
    (f'G01 Y360 Z{wrap_angle}')
    passes = passes_calculator(tube_thickness, length_1 + length_2 + length_3, p0, tow_width, wrap_angle)
    direction = 1
    current = 0 
    while current < passes:
        if direction == 1:
            print(lin_sec(p0, length_1, wrap_angle, direction))
        if direction == -1:
            print(lin_sec(p1, length_1, wrap_angle, direction))
        
        if direction == 1:
            segments = length_2 / 5
            pcurrent = p0
            while pcurrent< p1:
                samp = lin_sec(pcurrent, 5, wrap_angle, direction)
                print(samp)
                pcurrent = pcurrent + ((p1-p0)/segments)
        if direction == -1:
            segments = length_2 / 5
            pcurrent = p1
            while pcurrent > p0:
                samp = lin_sec(pcurrent, 5, wrap_angle, direction)
                print(samp)
                pcurrent = pcurrent - ((p1-p0)/segments)

        if direction == 1:
            print(lin_sec(p1, length_3, wrap_angle, direction))
        if direction == -1:
            print(lin_sec(p0, length_3, wrap_angle, direction))

        if direction == 1:
            cyc = 1
            print(gant_rot(p1, tow_width, wrap_angle, direction, cyc))
            cyc = -1
            print(gant_rot(p1, tow_width,wrap_angle, direction, cyc))
        if direction == -1:
            cyc = 1
            print(gant_rot(p0, tow_width, wrap_angle, direction, cyc))
            cyc = -1
            print(gant_rot(p0, tow_width, wrap_angle, direction, cyc))
        
        current += 1
        direction *= -1



## Helper Functions Below This Line ##
# <=======================================================================================================================================================>



# This functions returns one line of G-Code for a linear section dependant of direction
# Example command: G1 X(Dest_x) Y(Dest_y)
def lin_sec(p, length, wrap_angle, direction):
    Dest_X = length
    Dest_Y = round(((2 * 180 * (np.tan(np.radians(wrap_angle))) * length) / p), 3)
    if direction == 1:
        return f'G1 X{-Dest_X} Y{Dest_Y}'
    if direction == -1:
        return f'G1 X{Dest_X} Y{Dest_Y}'

      
# This functions returns one line of G-Code that controls half of the gantry's rotation
# Example command: G1 X(Dest_x) Y(Dest_y) Z(Dest_z)
def gant_rot(p, tow_width, wrap_angle, direction, cyc):
    Dest_x = 20
    Dest_y = round((360 + tow_width * np.degrees(np.arctan(tow_width/p))) / 2, 2)
    Dest_z = wrap_angle
    if direction == 1 and cyc == 1:
        return f'G1 X{-Dest_x} Y{Dest_y} Z{-Dest_z}'
    if direction == 1 and cyc == -1:
        return f'G1 X{Dest_x} Y{Dest_y} Z{-Dest_z}'
    if direction == -1 and cyc == 1:
        return f'G1 X{Dest_x} Y{Dest_y} Z{Dest_z}'
    if direction == -1 and cyc == -1:
        return f'G1 X{-Dest_x} Y{Dest_y} Z{Dest_z}'



# This fuction calcualtes the number of passes required to name a tube of the desired thickness
def passes_calculator(tube_thickness, length, perimeter, tow_width, wrap_angle):
    layers = round((tube_thickness * (8 / 1.6)), 0)
    passes = layers * ((length * perimeter) / (tow_width * np.sqrt((length ** 2) + (length * np.tan(np.radians(wrap_angle))) ** 2)))
    return passes


# This fuction is responsible for saving every line of G-Code to a file destination
def file_saver(string, file_name):
    file = open(f'{file_name}', "a")
    file.write(string + "\n")
    file.close()



#This function will add values for txt files to a list so that they can all be saved at once in order to speed up code
def add_to_string():
    return



# This function works with the UI to make sure that all inputs are valid and wont break the program
def int_checker(str):
    valid = False
    while not valid:
        value = float(input(f'{str}'))
        if value > 0:
            valid = True
        else: 
            print('Whoops! Please try again:)')
    return value
