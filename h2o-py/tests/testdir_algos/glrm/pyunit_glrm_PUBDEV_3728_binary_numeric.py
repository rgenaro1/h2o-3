from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator


def glrm_pubdev_3728_arrest():
  print("Importing prostate.csv data...")

  # frame binary data is read in as enums.  Let's see if it runs.
  prostateF = h2o.upload_file(pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"))
  prostateF_num = h2o.upload_file(pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"))
  prostateF_num[0] = prostateF_num[0].asnumeric()
  prostateF_num[4] = prostateF_num[4].asnumeric()

  loss_all = ["Logistic", "Quadratic", "Categorical", "Categorical", "Logistic", "Quadratic", "Quadratic", "Quadratic"]
  # glrm_h2o = H2OGeneralizedLowRankEstimator(k=5, loss_by_col=loss_all, recover_svd=True, transform="STANDARDIZE",
  #                                           seed=12345)
  # glrm_h2o.train(x=prostateF.names, training_frame=prostateF, validation_frame=prostateF)
  # glrm_h2o.show()

  # exercise logistic loss with numeric columns
  glrm_h2o_num = H2OGeneralizedLowRankEstimator(k=5, loss_by_col=loss_all, recover_svd=True, transform="STANDARDIZE",
                                                seed=12345)
  glrm_h2o_num.train(x=prostateF_num.names, training_frame=prostateF_num, validation_frame=prostateF_num)
  glrm_h2o_num.show()

if __name__ == "__main__":
  pyunit_utils.standalone_test(glrm_pubdev_3728_arrest)
else:
  glrm_pubdev_3728_arrest()
