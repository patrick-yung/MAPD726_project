package com.example.yungpakhongpatrick.mapd726_project

object StoreRepository {
    val stores = mutableListOf(
        StoreLocation("Walmart Toronto", 43.6532, -79.3832, "220 Yonge St, Toronto, ON", 4.2),
        StoreLocation("Costco North York", 43.7250, -79.4520, "100 Billy Bishop Way, North York, ON", 4.6),
        StoreLocation("Superstore Scarborough", 43.7764, -79.2318, "1755 Brimley Rd, Scarborough, ON", 4.3),
        StoreLocation("No Frills Etobicoke", 43.6205, -79.5132, "220 North Queen St, Etobicoke, ON", 4.1),
        StoreLocation("Loblaws Downtown Toronto", 43.6615, -79.3950, "60 Carlton St, Toronto, ON", 4.4),

        StoreLocation("Walmart Mississauga", 43.5890, -79.6441, "100 City Centre Dr, Mississauga, ON", 4.2),
        StoreLocation("Costco Mississauga", 43.6100, -79.6950, "1570 Dundas St E, Mississauga, ON", 4.5),
        StoreLocation("FreshCo Brampton", 43.7315, -79.7624, "398 Queen St E, Brampton, ON", 4.0)
    )

    fun resetStores() {
        stores.forEach { store ->
            store.isEnabled = true
        }
    }
}