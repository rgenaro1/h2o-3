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

  loss_all = ["Logistic", "Quadratic", "Categorical", "Categorical", "Logistic", "Quadratic", "Quadratic", "Quadratic"]
  glrm_h2o = H2OGeneralizedLowRankEstimator(k=5, loss_by_col=loss_all)
  glrm_h2o.train(x=prostateF.names, training_frame=prostateF)
  glrm_h2o.show()


if __name__ == "__main__":
  pyunit_utils.standalone_test(glrm_pubdev_3728_arrest)
else:
  glrm_pubdev_3728_arrest()
