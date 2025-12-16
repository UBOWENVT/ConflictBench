# Script to run experiments
from tabnanny import check

from openai import OpenAI
import google.generativeai as genai
import glob
import re
import os
import time
import logging
import shutil
import pandas as pd
from pathlib import Path
import pickle
from git import Repo
import subprocess
import requests

# Set path
path_prefix = '/home/ppp/Research_Projects/Merge_Conflicts'
workspace = 'Resource/workspace-bench'
logger_path = 'CodeBase/ConflictBench/LLM_Experiment/Logger'
data_path = 'CodeBase/ConflictBench/LLM_Experiment/Data'
output_path = 'Resource/output'


# Set constant
# Set the longest waiting time to wait for a task to execute (Unit: minutes)
MAX_WAITINGTIME_OpenAI = 5 * 60
MAX_WAITINGTIME_GEMINI = 5 * 60
SLEEP_TIME = 5


# Define Exception
class AbnormalBehaviourError(Exception):
    # Any user-defined abnormal behaviour need to terminate the script can be found here
    def __init__(self, message):
        self.message = message


def read_csv():
    # Read the origin csv file
    df = pd.read_csv(os.path.join(path_prefix,data_path,'ConflictBench.csv'))
    return df


def get_api_key(model):
    # API Keys, stored locally in environment
    # read the local api key and return
    if model == 'gemini':
        key = os.getenv('gemini_api_key')
        if key is None:
            raise AbnormalBehaviourError("Missing gemini_ap_key")
        else:
            return key
    elif model == 'openai':
        key = os.getenv('openai_api_key')
        if key is None:
            raise AbnormalBehaviourError("Missing openai_api_key")
        else:
            return key
    else:
        raise AbnormalBehaviourError('Model must be either gemini or openai')


def parse_text(content):
        pattern = (
            r"<Valid Conflict>:\s*(.*?)\s*"
            r"<Check Reason>:\s*(.*?)\s*"
            r"<Resolution Strategy>:\s*(.*?)\s*"
            r"<Resolution Content>:\s*(.*?)\s*"
            r"<Resolution Reason>:\s*(.*)"
        )
        match = re.search(pattern, content, re.DOTALL)
        if match:
            valid_conflict = match.group(1).strip()
            check_reason = match.group(2).strip()
            resolution_strategy = match.group(3).strip()
            resolution_content = match.group(4).strip()
            resolution_reason = match.group(5).strip()
            return valid_conflict, check_reason, resolution_strategy, resolution_content, resolution_reason
        else:
            return None, None, None


# Chose to continue experiment
# resume_experiment = True
resume_experiment = False

# create logger to record complete info
# create logger with 'llm_experimet_logger'
logger = logging.getLogger('llm_experimet_logger')
logger.setLevel(logging.INFO)
# create file handler which logs even debug messages
fh = logging.FileHandler(os.path.join(path_prefix, logger_path, 'llm_experimet.log'))
fh.setLevel(logging.INFO)
# create formatter and add it to the handlers
formatter = logging.Formatter('%(asctime)s - %(levelname)s - %(message)s')
fh.setFormatter(formatter)
# add the handlers to the logger
logger.addHandler(fh)

try:
    project_record = None
    # Check mode whether to resume
    if(resume_experiment):
        # Pick up where the previous experiment ends
        # Read pickle project_record
        if os.path.exists(os.path.join(path_prefix, data_path, "project_record.csv")) and \
                os.path.isfile(os.path.join(path_prefix, data_path, "project_record.csv")):
            project_record = pd.read_csv(os.path.join(path_prefix, data_path, "project_record.csv"))
        else:
            # Cannot find the data file, report error
            raise Exception("Cannot find project_record.csv")
    else:
        # Initiate project_record with reading csv
        if os.path.exists(os.path.join(path_prefix, data_path, 'ConflictBench.csv')) and \
                os.path.isfile(os.path.join(path_prefix, data_path, 'ConflictBench.csv')):
            project_record = read_csv()
            # Add new columns
            project_record['Processed'] = False
            project_record['OpenAI_capable'] = None
            project_record['OpenAI_check_valid_conflict'] = None
            project_record['OpenAI_check_reason'] = None
            project_record['OpenAI_resolution_strategy'] = None
            project_record['OpenAI_resolution_content'] = None
            project_record['OpenAI_resolution_reason'] = None
            project_record['OpenAI_raw_output'] = None
            project_record['Gemini_capable'] = None
            project_record['Gemini_check_valid_conflict'] = None
            project_record['Gemini_check_reason'] = None
            project_record['Gemini_resolution_strategy'] = None
            project_record['Gemini_resolution_content'] = None
            project_record['Gemini_resolution_reason'] = None
            project_record['Gemini_raw_output'] = None
    num_list, num_label = project_record.shape
    for i in range(num_list):       # Pass this line if it's already processed
        if project_record.loc[i,'Processed']:
            continue
        line = project_record.loc[i]
        # Update logger to start new line
        logger.info("Start processing index " + str(i+1) + "\tproject " + line['Project'] + " commit " + line['Commit'])
        
        # Get base_left_diff, base_right_diff info
        left_diff = line['LEFT VERSION\nCODE SNIPPET']
        right_diff = line['RIGHT VERSION\nCODE SNIPPET']
        conflict_chuck = line['MERGED VERSION\nCODE SNIPPET']
        # Prepare the system and user content
        system_content = (
            'You will be provided with a concrete example of merge conflict. Merge scenario contains 4 versions. Base'
            ' version, left version, right version and git-merged version. Git-merge version contain the merge '
            'conflict. You will be provided diff information between base version and left version, diff information '
            'between base version and right version, and the conflicting chunk reported by git-merge version. Firstly,'
            ' you need to check whether this merge conflict is a valid conflict. Valid conflict means left version and'
            ' right version apply divergent or incompatible edits to overlapping text regions. Otherwise, it’s not a'
            ' valid conflict. If it is a valid conflict, report it TRUE and explain why. If it’s not a valid conflict,'
            ' report it as FALSE. Secondly, if it’s not a valid conflict, try to resolve the conflict and report the '
            'resolution, strategy and reasoning. There are 7 resolution strategies: '
                 '1/ Pick left version.'
                 '2/ Pick right version.'
                 '3/ Pick left version plus some new manual edit.'
                 '4/ Pick right version plus some new manual edits.'
                 '5/ Combine both left and right versions, no new manual edits.'
                 '6/ Combine both left and right versions, plus some new manual edits.'
                 '7/ Purely new manual edits.'
            'Your output may be two cases.'
            'If it’s a valid conflict, your output will be:'
            '<Valid Conflict>: TRUE'
            '<Check Reason>: explain why you make the decision'            
            '<Resolution Strategy>: N/A'
            '<Resolution Content>: N/A'
            '<Resolution Reason>: N/A'
            'If it’s not a valid conflict, your output will be:'
            '<Valid Conflict>: FALSE'
            '<Check Reason>: explain why you make the decision' 
            '<Resolution Strategy>: 1 or 2 or 3 or 4 or 5 or 6 or 7'
            '<Resolution Content>: merge result'
            '<Resolution Reason>: explain how and why do you resolve the conflict'

            'First example:																						     '
            'Input:												                                                     '
            '<Left>:                                                                                                 '
            '- <version>1.8.1</version>                                                                              '
            '+ <version>1.9.7</version>                                                                              '
            '<Right>:                                                                                                '
            '- <version>1.8.1</version>                                                                              '
            '+ <version>1.9.3</version>                                                                              '
            '<Git-merge>:                                                                                            '
            '<<<<<<<                                                                                                 '
            ' <version>1.9.7</version>                                                                               '
            '=======                                                                                                 '
            ' <version>1.9.3</version>                                                                               '
            '>>>>>>>                                                                                                 '
            'Output:                                                                                                 '
            '<Valid Conflict>: TRUE                                                                                  '
            '<Check Reason>: left/right modify version number to different values. There is overlapping text region. '
            '<Resolution Strategy>: N/A                                                                              '
            '<Resolution Content>: N/A                                                                               '
            '<Resolution Reason>: N/A                                                                                '
            'Second example:                                                                                         '
            '<Left>:                                                                                                 '
            '                                                                                                        '
            '-        <module>eladmin-generator</module>                                                             '
            '     </modules>                                                                                         '
            '<Right>:                                                                                                '
            '                                                                                                        '
            '         <module>eladmin-system</module>                                                                '
            '         <module>eladmin-tools</module>                                                                 '
            '         <module>eladmin-generator</module>                                                             '
            '-    </modules>                                                                                         '
            '+                <module>eladmin-monitor</module>                                                       '
            '+        </modules>                                                                                     '
            '<Git-merge>:                                                                                            '
            '<<<<<<< HEAD                                                                                            '
            '    </modules>                                                                                          '
            '=======                                                                                                 '
            '        <module>eladmin-generator</module>                                                              '
            '                <module>eladmin-monitor</module>                                                        '
            '        </modules>                                                                                      '
            '>>>>>>> 68d7d00772c51a6a53d4c52f59176b5f6ee42f56                                                        '
            'Output:                                                                                                 '
            '<Valid Conflict>: FALSE                                                                                 '
            '<Check Reason>: left/right modify different lines. There is no overlapping text region.                 '
            '<Resolution Strategy>: 5                                                                                '
            '<Resolution Content>:                                                                                   '
            '         <module>eladmin-system</module>                                                                '
            '         <module>eladmin-tools</module>                                                                 '
            '-        <module>eladmin-generator</module>                                                             '
            '-    </modules>                                                                                         '
            '+                <module>eladmin-monitor</module>                                                       '
            '+        </modules>                                                                                     '
            '<Resolution Reason>: Combine edits from both left and right versions.                                   '
        )
        user_content = f'''
            The program diff between base version and left version is {left_diff}. The program diff between base version
            and right version is {right_diff}. The conflicting chunk reported by git merge is {conflict_chuck}. Please 
            check this merge conflict. Report it’s a valid conflict or not. If it’s not a valid conflict, resolve  
            this conflict.'''
        # Ask OpenAI to get answer
        client = OpenAI(api_key=get_api_key('openai'))
        completion = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[
                {"role": "system", "content": system_content},
                {"role": "user", "content": user_content}]
        )
        project_record.loc[i, 'OpenAI_capable'] = True
        if completion.choices[0].finish_reason == 'stop':
            valid_conflict, check_reason, resolution_strategy, resolution_content, resolution_reason = parse_text(completion.choices[0].message.content)
            project_record.loc[i, 'OpenAI_check_valid_conflict'] = valid_conflict
            project_record.loc[i, 'OpenAI_check_reason'] = check_reason
            project_record.loc[i, 'OpenAI_resolution_strategy'] = resolution_strategy
            project_record.loc[i, 'OpenAI_resolution_content'] = resolution_content
            project_record.loc[i, 'OpenAI_resolution_reason'] = resolution_reason
            project_record.loc[i, 'OpenAI_raw_output'] = completion.choices[0].message.content
            logger.info("OpenAI model finished.")
        else:
            logger.error("OpenAI fail to generate resolution due to " + completion.choices[0].finish_reason)
        # Sleep for a moment to avoid quota limit
        time.sleep(SLEEP_TIME)
        # Ask Gemini to get answer
        genai.configure(api_key=get_api_key('gemini'))
        model = genai.GenerativeModel(model_name="gemini-2.0-flash-exp",
                                      system_instruction=system_content)
        response = model.generate_content(user_content)
        # Write response into sheet
        project_record.loc[i, 'Gemini_capable'] = True
        if response.candidates[0].finish_reason == genai.protos.Candidate.FinishReason.STOP:
            valid_conflict, check_reason, resolution_strategy, resolution_content, resolution_reason = parse_text(response.text)
            project_record.loc[i, 'Gemini_check_valid_conflict'] = valid_conflict
            project_record.loc[i, 'Gemini_check_reason'] = check_reason
            project_record.loc[i, 'Gemini_resolution_strategy'] = resolution_strategy
            project_record.loc[i, 'Gemini_resolution_content'] = resolution_content
            project_record.loc[i, 'Gemini_resolution_reason'] = resolution_reason
            project_record.loc[i, 'Gemini_raw_output'] = response.text
            logger.info("Gemini model finished.")
        else:
            logger.error("Gemini fail to generate resolution due to " + response.candidates[0].finish_reason)
        # Sleep for a moment to avoid quota limit
        time.sleep(SLEEP_TIME)
        # Update logger to finish this line
        logger.info("Complete processing index " + str(i+1) + "\tproject " + line['Project'] + " commit " + line['Commit'])
        project_record.loc[i,'Processed'] = True
        # Write locally
        project_record.to_csv(os.path.join(path_prefix, data_path, 'project_record.csv'), index=False)
        # Finish all merge scenarios
except IOError as e:
    # IO Exception occur
    print(str(e))
    project_record.to_csv(os.path.join(path_prefix, data_path, 'project_record.csv'), index=False)
except Exception as e:
    # All other exceptions
    print(str(e))
    project_record.to_csv(os.path.join(path_prefix, data_path, 'project_record.csv'), index=False)
