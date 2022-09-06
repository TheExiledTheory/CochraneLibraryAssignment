#Load modules 
import json
import sys
import subprocess 

try: #Check to see if user has cloudscraper installed
    import cloudscraper
except (ImportError, ModuleNotFoundError):  #Install it! 
    subprocess.check_call([sys.executable, '-m', 'pip', 'install', 'cloudscraper'])

#Configure cloudscraper 
scraper = cloudscraper.create_scraper(delay=10, browser='chrome') 
url = "https://www.cochranelibrary.com/en/cdsr/reviews/topics?p_p_id=scolaristopics_WAR_scolaristopics&p_p_lifecycle=2&p_p_state=normal&p_p_mode=view&p_p_resource_id=topics-list&p_p_cacheability=cacheLevelPage"

#Grab return and convert str to json
try:
	info = scraper.get(url).text
except Exception as e:
    print(e)
    exit(1)
json_str = info
data = json.loads(json_str)

#Setup a list to hold titles 
return_data_list = []

#Add each title to the list 
for item in data:
    return_data_list.append(item['title'])

print(return_data_list)
#return_data_string = ''.join(return_data_list)
#print(return_data_string)
