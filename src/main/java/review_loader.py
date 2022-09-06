#Helper module for Lib.java - Used to retrieve number of pages for threads for exact thread allocation and to scrape review metadata  

#Load modules 
import json
import sys
import subprocess 

try: #Check to see if user has cloudscraper installed
    import cloudscraper
except (ImportError, ModuleNotFoundError):  #Install it and import it 
    subprocess.check_call([sys.executable, '-m', 'pip3', 'install', 'cloudscraper'])
    import cloudscraper

try : #Check to see if user has beautifulsoup4 installed
    from bs4 import BeautifulSoup
except (ImportError, ModuleNotFoundError): #Install it and import it
    subprocess.check_call([sys.executable, '-m', 'pip3', 'install', 'beautifulsoup4'])
    from bs4 import BeautifulSoup



#Make sure that the user entered required parameters 
#assert len(sys.argv) == 4, "A command, url, and topic must be provided!"

#review_loader.py "command" "url" "topic as adjusted string"
#CASE 1 - return the number of pages for a given topic
if (sys.argv[1] == "return_pages"): 
    #Check param
    if (sys.argv[2]): 
        #Verify url 
        if ("http" in sys.argv[2]): 
            
            #Get url and topic 
            url = sys.argv[2]
            topic = sys.argv[3] 

            #Setup cloudscraper 
            scraper = cloudscraper.create_scraper(delay=10, browser='chrome')

            #Configure headers 
            headers = {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.116 Safari/537.36',
                'Host': 'www.cochranelibrary.com',
                'Origin': 'https://www.cochranelibrary.com',
                'Referer': url,
                'Sec-Fetch-Mode': 'cors',
                'Sec-Fetch-Site': 'same-origin',
                'Sec-Fetch-Dest': 'empty',
                'Accept': 'text/html, */*; q=0.01',
                'Accept-Encoding': 'gzip, deflate, br',
                'Accept-Language': 'en-US,en;q=0.5',
                'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                'X-Requested-With': 'XMLHttpRequest',
                
                
            }

            #Configure body payload 
            body = f"displayText={topic}&searchText={topic}&searchType=basic&facetQueryField=topic_id&searchBy=13&orderBy=displayDate-true&facetDisplayName={topic}&pathname=%2Fsearch"

            # I tested the query string for multiple topics and it appears that the id of 20759 doesnt change between topics! ...luckily
            #I also tested with multiple post bodies as some of the body queries that I found in the network debugger has specific id's for each topic... once again ....luckily the post request proves successful without them!
            
            try :
                #Send POST
                info = scraper.post("https://www.cochranelibrary.com/en/c/portal/render_portlet?p_l_id=20759&p_p_id=scolarissearchresultsportlet_WAR_scolarissearchresults&p_p_lifecycle=0&p_t_lifecycle=0&p_p_state=normal&p_p_mode=view&p_p_col_id=column-1&p_p_col_pos=0&p_p_col_count=1&p_p_isolated=1&currentURL=/search", headers=headers, data=body)
            except Exception as e:
                print(e)
                exit(1)
            
            #Parse the html
            soup = BeautifulSoup(info.text, 'html.parser')

            #Find the total number of pages
            pagination = soup.find(class_="results-count")
            page_Total = pagination.find_all("span")[0].text

            #Convert to float and divide by results by page 
            real_page_total = float(page_Total)
            real_page_total = real_page_total / 25

            print(real_page_total)
            exit(0)
            


#review_loader.py "command" "url" "topic as adjusted string" "topic as clean string"
#CASE 2 - Return all reviews as a string 
elif (sys.argv[1] == "return_reviews"):
    #Check param
    if (sys.argv[2] != None): 
        #Verify url 
        if ("http" in sys.argv[2]): 

            #Set initials 
            url = sys.argv[2]                               #URL for the search 
            topic_adj = sys.argv[3]                         #Topic encoded for URL
            topic_clean = sys.argv[4]                       #Topic for output file
            location = url.index("cur=") + 4                #Search url for page     
            page_num = url[location:]                       #Page number for post           
            link_domain = "http:/onlinelibrary.wiley.com/"  #Link reference for output file 

            #Setup cloudscraper 
            scraper = cloudscraper.create_scraper(delay=10, browser='chrome')

            #Configure url for page # provided 
            post_url = "https://www.cochranelibrary.com/en/c/portal/render_portlet?p_l_id=20759&p_p_id=scolarissearchresultsportlet_WAR_scolarissearchresults&p_p_lifecycle=0&p_t_lifecycle=0&p_p_state=normal&p_p_mode=view&p_p_col_id=column-1&p_p_col_pos=0&p_p_col_count=1&p_p_isolated=1&currentURL=/search&cur=" + page_num

            #Configure headers 
            headers = {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.116 Safari/537.36',
                'Host': 'www.cochranelibrary.com',
                'Origin': 'https://www.cochranelibrary.com',
                'Referer': url,
                'Sec-Fetch-Mode': 'cors',
                'Sec-Fetch-Site': 'same-origin',
                'Sec-Fetch-Dest': 'empty',
                'Accept': 'text/html, */*; q=0.01',
                'Accept-Encoding': 'gzip, deflate, br',
                'Accept-Language': 'en-US,en;q=0.5',
                'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                'X-Requested-With': 'XMLHttpRequest',
                
                
            }
            
            #Configure body payload 
            body = f"displayText={topic_adj}&searchText={topic_adj}&searchType=basic&facetQueryField=topic_id&searchBy=13&orderBy=displayDate-true&facetDisplayName={topic_adj}&pathname=%2Fsearch"

            try :
                #Send POST
                info = scraper.post(post_url, headers=headers, data=body)
            except Exception as e:
                print(e)
                exit(1)

            #Parse the html
            soup = BeautifulSoup(info.text, 'html.parser')

            #Find the reviews section 
            results = soup.find(class_="search-results-section-body")
            reviews = results.find_all("div", class_="search-results-item")

            output = ""
            output = "Results for " + topic_clean + " from page " + page_num + "\n\n"

            #print(reviews)
            for review in reviews: 
                #Grab the title of the article 
                title_tag = review.find("h3", class_="result-title")
                final_title = title_tag.text
                
                #Grab the link to the article
                link_tag = title_tag.find("a")
                link_endpoint = link_tag.get("href")
                final_link = link_endpoint.replace("/cdsr/", link_domain)
                
                #Grab the authors of the article
                authors_tag = review.find("div", class_="search-result-authors")
                final_authors = authors_tag.text


                #Grab the data of the article
                date_tag = review.find("div", class_="search-result-date")
                final_date = date_tag.text

                #Format into a final text string 
                final_review = f"{final_link} | {topic_clean} |{final_title}|{final_authors}|{final_date}"
                
                output += final_review + "\n\n"

                
            print(output)

            #Output the return to a file 
            #f = open("test.txt", "a")
            #f.write(str(info.text))
            #f.close()

            exit(0)
            



