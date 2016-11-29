setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


# This test is used for Wendy to check the run time.  Copied from what Tomas N has done
# for GLRM timing test.
test.RdocGBM.golden <- function() {

    num_runs = 200  # no need to be hugh, it happens about one run in six, I was told
    ausPath <- locate("smalldata/extdata/australia.csv")
    australia.hex <- h2o.uploadFile(path = ausPath)
    independent<- c("premax", "salmax","minairtemp", "maxairtemp", "maxsst", "maxsoilmoist", "Max_czcs")
    dependent<- "runoffnew"

    browser()
    
    # gaussian
    print("running Gaussian GBM")
    gaussian_model = h2o.gbm(y = dependent, x = independent, training_frame = australia.hex, ntrees = 10, max_depth = 3, min_rows = 2, learn_rate = 0.2, distribution= "gaussian")
    results_g = c(gaussian_model@parameters$seed, gaussian_model@model$run_time)
    names(results_g) <- c("seed", "run time [ms]")


    for(i in 1:num_runs){
        gaussian_model = h2o.gbm(y = dependent, x = independent, training_frame = australia.hex, ntrees = 10, max_depth = 3, min_rows = 2, learn_rate = 0.2, distribution= "gaussian")
        results_g = rbind(results_g, c(gaussian_model@parameters$seed, gaussian_model@model$run_time))
    }
    print(results_g)

    # multinomial (coerce response to factor. "AUTO" recognize this as a multinomial classification problem)
    print("running Multinomial GBM")
    australia.hex$runoffnew <- as.factor(australia.hex$runoffnew)
    multinomial_model = h2o.gbm(y = dependent, x = independent, training_frame = australia.hex, ntrees = 15, max_depth = 5, min_rows = 2, learn_rate = 0.01, distribution= "multinomial")
    results_m = c(multinomial_model@parameters$seed, multinomial_model@model$run_time)
    names(results_m) <- c("seed", "run time [ms]")
    
    for(i in 1:num_runs){
        multinomial_model = h2o.gbm(y = dependent, x = independent, training_frame = australia.hex, ntrees = 15, max_depth = 5, min_rows = 2, learn_rate = 0.01, distribution= "multinomial")
        results_m = rbind(results_m, c(multinomial_model@parameters$seed, multinomial_model@model$run_time))
        print(results_m)
    }
    print(results_m)
}

doTest("R Doc GBM", test.RdocGBM.golden)

