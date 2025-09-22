# AndroidX Migration Script

# This script reads artifact and class mappings from CSV files and applies them
# to all .java and .xml files in a specified source directory.

import os
import csv

# --- Configuration ---
# The script assumes that the '@temp' and '@app' directories are located
# in the same directory where the script is run.
ARTIFACT_MAPPING_FILE = os.path.join('temp', 'androidx-artifact-mapping.csv')
CLASS_MAPPING_FILE = os.path.join('temp', 'androidx-class-mapping.csv')
SOURCE_DIRECTORY = os.path.join('app', 'src', 'main')


def load_mappings(file_path):
    '''Loads mappings from a CSV file into a dictionary.'''
    mappings = {}
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            reader = csv.reader(f)
            next(reader)  # Skip header row
            for row in reader:
                if len(row) >= 2 and row[0] and row[1]:
                    mappings[row[0]] = row[1]
    except FileNotFoundError:
        print(f"Warning: Mapping file not found at {file_path}")
    except Exception as e:
        print(f"Error reading {file_path}: {e}")
    return mappings

def main():
    '''Main function to run the migration.'''
    print("--- Starting AndroidX Migration Script ---")

    # 1. Load both artifact and class mappings
    artifact_mappings = load_mappings(ARTIFACT_MAPPING_FILE)
    class_mappings = load_mappings(CLASS_MAPPING_FILE)
    
    all_mappings = {**artifact_mappings, **class_mappings}

    if not all_mappings:
        print("No mappings found. Exiting.")
        return

    # Sort keys by length, descending. This is crucial to replace 
    # 'android.support.v4.app.FragmentActivity' before 'android.support.v4.app'.
    sorted_keys = sorted(all_mappings.keys(), key=len, reverse=True)

    print(f"Loaded {len(all_mappings)} total mappings.")
    print(f"Scanning for .java and .xml files in: {SOURCE_DIRECTORY}\n")

    # 2. Walk through the source directory
    total_files_processed = 0
    total_replacements = 0

    for root, _, files in os.walk(SOURCE_DIRECTORY):
        for file in files:
            if file.endswith('.java') or file.endswith('.xml'):
                file_path = os.path.join(root, file)
                file_replacements = 0
                
                try:
                    with open(file_path, 'r', encoding='utf-8') as f:
                        content = f.read()
                        original_content = content

                    # 3. Perform replacements for the current file
                    for old_string in sorted_keys:
                        new_string = all_mappings[old_string]
                        if old_string in content:
                            count = content.count(old_string)
                            content = content.replace(old_string, new_string)
                            file_replacements += count

                    # 4. Write changes back to the file if modifications were made
                    if content != original_content:
                        with open(file_path, 'w', encoding='utf-8') as f:
                            f.write(content)
                        print(f"- Modified {file_path} ({file_replacements} replacements)")
                        total_replacements += file_replacements

                    total_files_processed += 1

                except Exception as e:
                    print(f"Error processing file {file_path}: {e}")

    print(f"\n--- Migration Complete ---")
    print(f"Processed {total_files_processed} files.")
    print(f"Made a total of {total_replacements} replacements.")

if __name__ == '__main__':
    main()
