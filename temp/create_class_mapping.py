'''
This script reads an HTML file, extracts the first table, 
and saves it as a CSV file with 'Type', 'Old', and 'New' columns.
'''
import pandas as pd
import os

# Define file paths
# Assuming @temp is a placeholder for a temporary directory
# For this script, we assume the script is run from a location
# where '@temp' is a subdirectory.
# If @temp is an absolute path, replace it accordingly.
temp_dir = '@temp'
html_file = os.path.join(temp_dir, 'index.html')
artifact_csv = os.path.join(temp_dir, '構件對應.csv') # Referenced for format
output_csv = os.path.join(temp_dir, '類別對應.csv')

def convert_html_table_to_csv():
    '''
    Extracts a table from an HTML file and saves it to a CSV file.
    '''
    print(f"Reading HTML file from: {html_file}")

    if not os.path.exists(html_file):
        print(f"Error: HTML file not found at {html_file}")
        return

    try:
        # pandas.read_html reads all tables from a URL or file-like object
        # It returns a list of DataFrames
        tables = pd.read_html(html_file, encoding='utf-8')
        
        if not tables:
            print("No tables found in the HTML file.")
            return

        # Assume the first table is the one we want
        df = tables[0]
        
        print("Successfully extracted a table with the following columns:", df.columns.tolist())

        # Ensure the table has at least two columns
        if df.shape[1] < 2:
            print("Error: The extracted table does not have at least two columns.")
            return

        # Rename columns to 'Old' and 'New' based on their position
        df.columns = ['Old', 'New'] + df.columns[2:].tolist()
        df = df[['Old', 'New']]

        # Add the 'Type' column, referencing the format from 構件對應.csv
        df.insert(0, 'Type', 'Class')

        # Save the DataFrame to a CSV file
        df.to_csv(output_csv, index=False, encoding='utf-8-sig')
        
        print(f"Successfully created the class mapping CSV at: {output_csv}")

    except Exception as e:
        print(f"An error occurred: {e}")

if __name__ == "__main__":
    # Create a dummy @temp directory and files for testing if they don't exist
    if not os.path.exists(temp_dir):
        os.makedirs(temp_dir)
    if not os.path.exists(html_file):
        print(f"Creating dummy HTML file for demonstration: {html_file}")
        dummy_html_content = '''
        <html>
        <head><title>Test</title></head>
        <body>
            <h1>Class Mappings</h1>
            <table>
              <thead>
                <tr>
                  <th>Old support library class</th>
                  <th>AndroidX class</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>android.support.v4.app.Fragment</td>
                  <td>androidx.fragment.app.Fragment</td>
                </tr>
                <tr>
                  <td>android.support.v7.app.AppCompatActivity</td>
                  <td>androidx.appcompat.app.AppCompatActivity</td>
                </tr>
              </tbody>
            </table>
        </body>
        </html>
        '''
        with open(html_file, 'w', encoding='utf-8') as f:
            f.write(dummy_html_content)

    convert_html_table_to_csv()
